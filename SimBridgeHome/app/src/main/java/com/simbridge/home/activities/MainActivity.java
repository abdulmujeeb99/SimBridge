package com.simbridge.home.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.home.R;
import com.simbridge.home.services.RelayService;
import com.simbridge.home.services.SignalingService;
import com.simbridge.home.utils.AppConfig;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvPairCode, tvServerUrl, tvDeviceId;
    private TextView tvCallStatus, tvSmsStatus;
    private ImageView ivStatusDot;
    private Switch switchRelay;
    private LinearLayout cardConnection, cardSim, cardCalls, cardSms;

    private boolean relayRunning = false;

    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SignalingService.ACTION_PAIRED.equals(action)) {
                updateStatus("Connected", "Travel device paired ✓", true);
            } else if (SignalingService.ACTION_PEER_DISCONNECTED.equals(action)) {
                updateStatus("Waiting", "Travel device disconnected", false);
            } else if (SignalingService.ACTION_CALL_INCOMING.equals(action)) {
                tvCallStatus.setText("Incoming call...");
            } else if (SignalingService.ACTION_CALL_ENDED.equals(action)) {
                tvCallStatus.setText("No active call");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_home);
        bindViews();
        loadConfig();
        checkPermissions();
        registerReceivers();

        switchRelay.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) startRelay();
            else stopRelay();
        });

        findViewById(R.id.btnSetup).setOnClickListener(v ->
                startActivity(new Intent(this, SetupActivity.class)));

        // Auto start if setup done
        if (AppConfig.isSetupDone(this)) {
            switchRelay.setChecked(true);
            startRelay();
        }
    }

    private void bindViews() {
        tvStatus      = findViewById(R.id.tvStatus);
        tvPairCode    = findViewById(R.id.tvPairCode);
        tvServerUrl   = findViewById(R.id.tvServerUrl);
        tvDeviceId    = findViewById(R.id.tvDeviceId);
        tvCallStatus  = findViewById(R.id.tvCallStatus);
        tvSmsStatus   = findViewById(R.id.tvSmsStatus);
        ivStatusDot   = findViewById(R.id.ivStatusDot);
        switchRelay   = findViewById(R.id.switchRelay);
    }

    private void loadConfig() {
        tvPairCode.setText(AppConfig.getPairCode(this));
        tvServerUrl.setText(AppConfig.getServerUrl(this));
        tvDeviceId.setText(AppConfig.getDeviceId(this));
    }

    private void startRelay() {
        Intent intent = new Intent(this, RelayService.class);
        startForegroundService(intent);
        relayRunning = true;
        updateStatus("Active", "Connecting to server...", false);
    }

    private void stopRelay() {
        Intent intent = new Intent(this, RelayService.class);
        stopService(intent);
        relayRunning = false;
        updateStatus("Stopped", "Relay is off", false);
    }

    private void updateStatus(String status, String detail, boolean paired) {
        runOnUiThread(() -> {
            tvStatus.setText(status);
            ivStatusDot.setColorFilter(paired ?
                    getColor(R.color.green) : getColor(R.color.orange));
        });
    }

    private void registerReceivers() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SignalingService.ACTION_PAIRED);
        filter.addAction(SignalingService.ACTION_PEER_DISCONNECTED);
        filter.addAction(SignalingService.ACTION_CALL_INCOMING);
        filter.addAction(SignalingService.ACTION_CALL_ENDED);
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, filter);
    }

    private void checkPermissions() {
        List<String> missing = new ArrayList<>();
        for (String perm : REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED)
                missing.add(perm);
        }
        if (!missing.isEmpty())
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), 100);
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver);
        super.onDestroy();
    }
}
