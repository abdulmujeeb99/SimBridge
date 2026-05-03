package com.simbridge.travel.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Vibrator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.travel.R;
import com.simbridge.travel.services.ConnectionService;

public class IncomingCallActivity extends AppCompatActivity {

    private TextView tvCallerNumber, tvSimLabel;
    private ConnectionService connectionService;
    private boolean serviceBound = false;
    private String callerNumber, simLabel, homeDeviceId;
    private Vibrator vibrator;

    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder) service).getService();
            serviceBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) { serviceBound = false; }
    };

    private final BroadcastReceiver callEndedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopVibrateAndFinish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incoming_call);

        callerNumber = getIntent().getStringExtra("callerNumber");
        simLabel     = getIntent().getStringExtra("simLabel");
        homeDeviceId = getIntent().getStringExtra("homeDeviceId");

        tvCallerNumber = findViewById(R.id.tvCallerNumber);
        tvSimLabel     = findViewById(R.id.tvSimLabel);

        tvCallerNumber.setText(callerNumber != null ? callerNumber : "Unknown");
        tvSimLabel.setText(simLabel != null ? "via " + simLabel : "via Home SIM");

        findViewById(R.id.btnAnswer).setOnClickListener(v -> answerCall());
        findViewById(R.id.btnReject).setOnClickListener(v -> rejectCall());

        startVibrate();
        bindService(new Intent(this, ConnectionService.class), serviceConn, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(callEndedReceiver,
                new IntentFilter(ConnectionService.ACTION_CALL_ENDED));
    }

    private void answerCall() {
        stopVibrate();
        if (serviceBound) connectionService.sendCallAnswered(homeDeviceId);
        Intent intent = new Intent(this, ActiveCallActivity.class);
        intent.putExtra("number",       callerNumber);
        intent.putExtra("direction",    "incoming");
        intent.putExtra("simLabel",     simLabel);
        intent.putExtra("homeDeviceId", homeDeviceId);
        startActivity(intent);
        finish();
    }

    private void rejectCall() {
        stopVibrateAndFinish();
        if (serviceBound) connectionService.sendCallRejected(homeDeviceId);
    }

    private void startVibrate() {
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.vibrate(new long[]{0, 1000, 500}, 0);
    }

    private void stopVibrate() {
        if (vibrator != null) vibrator.cancel();
    }

    private void stopVibrateAndFinish() {
        stopVibrate();
        finish();
    }

    @Override
    protected void onDestroy() {
        stopVibrate();
        if (serviceBound) unbindService(serviceConn);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callEndedReceiver);
        super.onDestroy();
    }
}
