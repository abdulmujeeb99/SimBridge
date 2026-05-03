package com.simbridge.travel.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.travel.R;
import com.simbridge.travel.services.ConnectionService;

import java.util.ArrayList;
import java.util.List;

public class DialerActivity extends AppCompatActivity {

    private TextView tvDialNumber;
    private Spinner spinnerSim;
    private ConnectionService connectionService;
    private boolean serviceBound = false;
    private StringBuilder dialedNumber = new StringBuilder();

    // Currently selected home: [label, deviceId]
    private String selectedHomeId    = null;
    private String selectedHomeLabel = null;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder) service).getService();
            serviceBound = true;
            populateSimSpinner();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { serviceBound = false; }
    };

    private final BroadcastReceiver homeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Refresh spinner if a home connects/disconnects
            populateSimSpinner();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialer);

        tvDialNumber = findViewById(R.id.tvDialNumber);
        spinnerSim   = findViewById(R.id.spinnerSim);

        int[] btnIds = { R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3,
                         R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7,
                         R.id.btn8, R.id.btn9, R.id.btnStar, R.id.btnHash };
        String[] digits = {"0","1","2","3","4","5","6","7","8","9","*","#"};

        for (int i = 0; i < btnIds.length; i++) {
            final String d = digits[i];
            findViewById(btnIds[i]).setOnClickListener(v -> appendDigit(d));
        }
        findViewById(R.id.btn0).setOnLongClickListener(v -> { appendDigit("+"); return true; });
        findViewById(R.id.btnBackspace).setOnClickListener(v -> {
            if (dialedNumber.length() > 0) {
                dialedNumber.deleteCharAt(dialedNumber.length() - 1);
                tvDialNumber.setText(dialedNumber.toString());
            }
        });
        findViewById(R.id.btnCall).setOnClickListener(v -> placeCall());

        IntentFilter f = new IntentFilter();
        f.addAction(ConnectionService.ACTION_HOME_CONNECTED);
        f.addAction(ConnectionService.ACTION_HOME_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(homeChangeReceiver, f);

        bindService(new Intent(this, ConnectionService.class), serviceConn, Context.BIND_AUTO_CREATE);
    }

    private void populateSimSpinner() {
        if (!serviceBound) return;
        List<String[]> homes = connectionService.getHomeList(); // [label, deviceId]
        List<String> labels = new ArrayList<>();
        for (String[] h : homes) labels.add(h[0]); // show label in spinner

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSim.setAdapter(adapter);

        spinnerSim.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedHomeLabel = homes.get(pos)[0];
                selectedHomeId    = homes.get(pos)[1];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Auto-select first
        if (!homes.isEmpty()) {
            selectedHomeLabel = homes.get(0)[0];
            selectedHomeId    = homes.get(0)[1];
        }
    }

    private void appendDigit(String digit) {
        dialedNumber.append(digit);
        tvDialNumber.setText(dialedNumber.toString());
    }

    private void placeCall() {
        String number = dialedNumber.toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Enter a number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!serviceBound || !connectionService.isPaired()) {
            Toast.makeText(this, "No home device connected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedHomeId == null) {
            Toast.makeText(this, "Select a SIM", Toast.LENGTH_SHORT).show();
            return;
        }
        connectionService.sendCallOutgoing(number, selectedHomeId);

        Intent intent = new Intent(this, ActiveCallActivity.class);
        intent.putExtra("number",       number);
        intent.putExtra("direction",    "outgoing");
        intent.putExtra("simLabel",     selectedHomeLabel);
        intent.putExtra("homeDeviceId", selectedHomeId);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) unbindService(serviceConn);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(homeChangeReceiver);
        super.onDestroy();
    }
}
