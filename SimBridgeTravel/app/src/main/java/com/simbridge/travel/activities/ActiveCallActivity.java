package com.simbridge.travel.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.travel.R;
import com.simbridge.travel.services.ConnectionService;

public class ActiveCallActivity extends AppCompatActivity {

    private TextView tvCallNumber, tvCallTimer, tvCallDirection, tvSimLabel;
    private ImageButton btnMute, btnSpeaker, btnEnd;
    private ConnectionService connectionService;
    private boolean serviceBound = false;
    private boolean muted = false, speaker = false;
    private int elapsedSeconds = 0;
    private String homeDeviceId, simLabel;
    private Handler timerHandler = new Handler();

    private final Runnable timerRunnable = new Runnable() {
        @Override public void run() {
            elapsedSeconds++;
            tvCallTimer.setText(String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder) service).getService();
            serviceBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) { serviceBound = false; }
    };

    private final BroadcastReceiver callEndedReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            timerHandler.removeCallbacks(timerRunnable);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_call);

        String number   = getIntent().getStringExtra("number");
        String direction= getIntent().getStringExtra("direction");
        simLabel        = getIntent().getStringExtra("simLabel");
        homeDeviceId    = getIntent().getStringExtra("homeDeviceId");

        tvCallNumber    = findViewById(R.id.tvCallNumber);
        tvCallTimer     = findViewById(R.id.tvCallTimer);
        tvCallDirection = findViewById(R.id.tvCallDirection);
        tvSimLabel      = findViewById(R.id.tvSimLabel);
        btnMute         = findViewById(R.id.btnMute);
        btnSpeaker      = findViewById(R.id.btnSpeaker);
        btnEnd          = findViewById(R.id.btnEnd);

        tvCallNumber.setText(number != null ? number : "Unknown");
        tvCallDirection.setText("incoming".equals(direction) ? "Incoming call" : "Calling...");
        tvSimLabel.setText(simLabel != null ? simLabel : "Home SIM");

        timerHandler.postDelayed(timerRunnable, 1000);
        btnMute.setOnClickListener(v -> toggleMute());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnEnd.setOnClickListener(v -> endCall());

        bindService(new Intent(this, ConnectionService.class), serviceConn, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(callEndedReceiver,
                new IntentFilter(ConnectionService.ACTION_CALL_ENDED));
    }

    private void toggleMute() {
        muted = !muted;
        ((AudioManager) getSystemService(AUDIO_SERVICE)).setMicrophoneMute(muted);
        btnMute.setColorFilter(getColor(muted ? R.color.accent_cyan : R.color.text_muted));
        Toast.makeText(this, muted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void toggleSpeaker() {
        speaker = !speaker;
        ((AudioManager) getSystemService(AUDIO_SERVICE)).setSpeakerphoneOn(speaker);
        btnSpeaker.setColorFilter(getColor(speaker ? R.color.accent_cyan : R.color.text_muted));
    }

    private void endCall() {
        timerHandler.removeCallbacks(timerRunnable);
        if (serviceBound) connectionService.sendCallEnded(homeDeviceId);
        finish();
    }

    @Override public void onBackPressed() { /* block back during call */ }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        if (serviceBound) unbindService(serviceConn);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callEndedReceiver);
        super.onDestroy();
    }
}
