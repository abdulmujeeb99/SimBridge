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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.travel.R;
import com.simbridge.travel.services.ConnectionService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsActivity extends AppCompatActivity {

    private EditText etRecipient, etMessage;
    private LinearLayout llMessages;
    private ScrollView scrollView;
    private Spinner spinnerSim;
    private ConnectionService connectionService;
    private boolean serviceBound = false;
    private String selectedHomeId = null;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder) service).getService();
            serviceBound = true;
            populateSimSpinner();
        }
        @Override public void onServiceDisconnected(ComponentName name) { serviceBound = false; }
    };

    private final BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String data   = intent.getStringExtra(ConnectionService.EXTRA_DATA);
            if (ConnectionService.ACTION_SMS_INCOMING.equals(action)) handleIncomingSms(data);
            else if (ConnectionService.ACTION_SMS_SENT.equals(action)) handleSmsSent(data);
            else if (ConnectionService.ACTION_HOME_CONNECTED.equals(action) ||
                     ConnectionService.ACTION_HOME_DISCONNECTED.equals(action)) populateSimSpinner();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms);

        etRecipient = findViewById(R.id.etRecipient);
        etMessage   = findViewById(R.id.etMessage);
        llMessages  = findViewById(R.id.llMessages);
        scrollView  = findViewById(R.id.scrollView);
        spinnerSim  = findViewById(R.id.spinnerSim);

        findViewById(R.id.btnSend).setOnClickListener(v -> sendSms());

        IntentFilter f = new IntentFilter();
        f.addAction(ConnectionService.ACTION_SMS_INCOMING);
        f.addAction(ConnectionService.ACTION_SMS_SENT);
        f.addAction(ConnectionService.ACTION_HOME_CONNECTED);
        f.addAction(ConnectionService.ACTION_HOME_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).registerReceiver(smsReceiver, f);
        bindService(new Intent(this, ConnectionService.class), serviceConn, Context.BIND_AUTO_CREATE);
    }

    private void populateSimSpinner() {
        if (!serviceBound) return;
        List<String[]> homes = connectionService.getHomeList();
        List<String> labels = new ArrayList<>();
        for (String[] h : homes) labels.add(h[0]);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSim.setAdapter(adapter);

        spinnerSim.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedHomeId = homes.get(pos)[1];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        if (!homes.isEmpty()) selectedHomeId = homes.get(0)[1];
    }

    private void sendSms() {
        String to   = etRecipient.getText().toString().trim();
        String body = etMessage.getText().toString().trim();
        if (to.isEmpty() || body.isEmpty()) {
            Toast.makeText(this, "Enter recipient and message", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!serviceBound || !connectionService.isPaired() || selectedHomeId == null) {
            Toast.makeText(this, "No home device connected", Toast.LENGTH_SHORT).show();
            return;
        }
        connectionService.sendSms(to, body, selectedHomeId);
        addMessageBubble("To: " + to + "\n" + body, true, "Sending...", null);
        etMessage.setText("");
    }

    private void handleIncomingSms(String json) {
        try {
            JsonObject msg  = JsonParser.parseString(json).getAsJsonObject();
            String sender   = msg.get("sender").getAsString();
            String body     = msg.get("body").getAsString();
            String label    = msg.has("simLabel") ? msg.get("simLabel").getAsString() : "SIM";
            long ts         = msg.get("timestamp").getAsLong();
            String time     = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
            runOnUiThread(() -> addMessageBubble("From: " + sender + "\n" + body, false, time, label));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleSmsSent(String json) {
        try {
            JsonObject msg  = JsonParser.parseString(json).getAsJsonObject();
            boolean success = msg.get("success").getAsBoolean();
            runOnUiThread(() -> Toast.makeText(this,
                    success ? "SMS sent ✓" : "SMS failed ✗", Toast.LENGTH_SHORT).show());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void addMessageBubble(String text, boolean outgoing, String time, String simLabel) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(outgoing ? 80 : 8, 4, outgoing ? 8 : 80, 4);
        bubble.setLayoutParams(params);
        bubble.setPadding(16, 10, 16, 10);
        bubble.setBackground(getDrawable(outgoing ? R.drawable.bubble_outgoing : R.drawable.bubble_incoming));

        // SIM label badge for incoming
        if (!outgoing && simLabel != null) {
            TextView tvLabel = new TextView(this);
            tvLabel.setText(simLabel);
            tvLabel.setTextColor(0xFF00D4FF);
            tvLabel.setTextSize(10);
            bubble.addView(tvLabel);
        }

        TextView tvText = new TextView(this);
        tvText.setText(text);
        tvText.setTextColor(0xFFFFFFFF);
        tvText.setTextSize(14);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextColor(0xFFAAAAAA);
        tvTime.setTextSize(10);

        bubble.addView(tvText);
        bubble.addView(tvTime);
        llMessages.addView(bubble);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) unbindService(serviceConn);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(smsReceiver);
        super.onDestroy();
    }
}
