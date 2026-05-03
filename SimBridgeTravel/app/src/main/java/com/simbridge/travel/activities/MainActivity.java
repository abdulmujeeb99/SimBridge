package com.simbridge.travel.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.travel.R;
import com.simbridge.travel.services.ConnectionService;
import com.simbridge.travel.utils.AppConfig;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvPairCode, tvServer;
    private ImageView ivStatusDot;
    private LinearLayout tabDialer, tabSms, tabSettings;

    private ConnectionService connectionService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder) service).getService();
            serviceBound = true;
            updateStatusUI();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { serviceBound = false; }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectionService.ACTION_PAIRED.equals(action)) {
                setStatus("Paired", true);
            } else if (ConnectionService.ACTION_PEER_DISCONNECTED.equals(action)) {
                setStatus("Waiting...", false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_travel);

        tvStatus    = findViewById(R.id.tvStatus);
        tvPairCode  = findViewById(R.id.tvPairCode);
        tvServer    = findViewById(R.id.tvServer);
        ivStatusDot = findViewById(R.id.ivStatusDot);
        tabDialer   = findViewById(R.id.tabDialer);
        tabSms      = findViewById(R.id.tabSms);
        tabSettings = findViewById(R.id.tabSettings);

        tvPairCode.setText(AppConfig.getPairCode(this).isEmpty() ? "Not set" : AppConfig.getPairCode(this));
        tvServer.setText(AppConfig.getServerUrl(this));

        tabDialer.setOnClickListener(v -> startActivity(new Intent(this, DialerActivity.class)));
        tabSms.setOnClickListener(v -> startActivity(new Intent(this, SmsActivity.class)));
        tabSettings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));

        checkPermissions();
        registerReceivers();

        if (!AppConfig.isSetupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
        } else {
            startConnectionService();
        }
    }

    private void startConnectionService() {
        Intent intent = new Intent(this, ConnectionService.class);
        startForegroundService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void updateStatusUI() {
        if (serviceBound) {
            if (connectionService.isPaired()) setStatus("Paired ✓", true);
            else if (connectionService.isConnected()) setStatus("Connected", false);
            else setStatus("Connecting...", false);
        }
    }

    private void setStatus(String text, boolean paired) {
        runOnUiThread(() -> {
            tvStatus.setText(text);
            ivStatusDot.setColorFilter(getColor(paired ? R.color.green : R.color.orange));
        });
    }

    private void registerReceivers() {
        IntentFilter f = new IntentFilter();
        f.addAction(ConnectionService.ACTION_PAIRED);
        f.addAction(ConnectionService.ACTION_PEER_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, f);
    }

    private void checkPermissions() {
        List<String> missing = new ArrayList<>();
        String[] perms = { Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS };
        for (String p : perms)
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty())
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 100);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) unbindService(serviceConnection);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
        super.onDestroy();
    }
}
