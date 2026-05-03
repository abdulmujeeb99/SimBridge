package com.simbridge.home.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.home.services.SignalingService;

public class CallReceiver extends BroadcastReceiver {

    private static final String TAG = "CallReceiver";
    private static String lastState = TelephonyManager.EXTRA_STATE_IDLE;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (state == null) return;

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state) &&
                !TelephonyManager.EXTRA_STATE_RINGING.equals(lastState)) {
            // New incoming call
            Log.d(TAG, "Incoming call from: " + incomingNumber);
            notifyIncomingCall(context, incomingNumber != null ? incomingNumber : "Unknown");
        }

        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) &&
                !TelephonyManager.EXTRA_STATE_IDLE.equals(lastState)) {
            // Call ended or rejected
            Log.d(TAG, "Call ended");
            notifyCallEnded(context);
        }

        lastState = state;
    }

    private void notifyIncomingCall(Context context, String number) {
        // Start SignalingService if not running, then notify
        Intent serviceIntent = new Intent(context, SignalingService.class);
        context.startService(serviceIntent);

        // Broadcast to RelayService via local broadcast
        Intent broadcast = new Intent("simbridge.TELEPHONY_INCOMING");
        broadcast.putExtra("number", number);
        broadcast.putExtra("callId", String.valueOf(System.currentTimeMillis()));
        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast);
    }

    private void notifyCallEnded(Context context) {
        Intent broadcast = new Intent("simbridge.TELEPHONY_ENDED");
        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast);
    }
}
