package com.simbridge.home.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.home.services.SignalingService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Method;

public class CallManager {

    private static final String TAG = "CallManager";

    private final Context context;
    private final SignalingService signalingService;
    private String pendingCallId;
    private String pendingCallerNumber;

    public CallManager(Context context, SignalingService signalingService) {
        this.context = context;
        this.signalingService = signalingService;
        registerTelephonyReceiver();
    }

    private void registerTelephonyReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("simbridge.TELEPHONY_INCOMING");
        filter.addAction("simbridge.TELEPHONY_ENDED");
        LocalBroadcastManager.getInstance(context).registerReceiver(telephonyReceiver, filter);
    }

    private final BroadcastReceiver telephonyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if ("simbridge.TELEPHONY_INCOMING".equals(action)) {
                String number = intent.getStringExtra("number");
                String callId = intent.getStringExtra("callId");
                pendingCallerNumber = number;
                pendingCallId = callId;
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                        .getInstance(context)
                        .sendBroadcast(new Intent(SignalingService.ACTION_CALL_INCOMING));
                // Notify travel device
                signalingService.sendCallIncoming(number, callId);
                Log.d(TAG, "Notified travel of incoming: " + number);
            } else if ("simbridge.TELEPHONY_ENDED".equals(action)) {
                signalingService.sendCallEnded();
                pendingCallId = null;
                pendingCallerNumber = null;
            }
        }
    };

    // Travel device wants to place an outgoing call
    public void placeOutgoingCall(String json) {
        try {
            JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
            String number = msg.get("number").getAsString();
            Log.d(TAG, "Placing outgoing call to: " + number);

            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(callIntent);
        } catch (Exception e) {
            Log.e(TAG, "placeOutgoingCall error: " + e.getMessage());
        }
    }

    // Travel device answered — we pick up the ringing call on home device
    public void answerIncomingCall() {
        Log.d(TAG, "Answering incoming call");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) tm.acceptRingingCall();
            } else {
                // Fallback for older Android via reflection
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                Method answer = tm.getClass().getMethod("answerRingingCall");
                answer.invoke(tm);
            }
        } catch (Exception e) {
            Log.e(TAG, "answerIncomingCall error: " + e.getMessage());
        }
    }

    // Travel device rejected
    public void rejectIncomingCall() {
        Log.d(TAG, "Rejecting incoming call");
        try {
            endCallViaReflection();
        } catch (Exception e) {
            Log.e(TAG, "rejectIncomingCall: " + e.getMessage());
        }
    }

    // End active call
    public void endCall() {
        Log.d(TAG, "Ending call");
        try {
            endCallViaReflection();
        } catch (Exception e) {
            Log.e(TAG, "endCall: " + e.getMessage());
        }
    }

    // Send DTMF tone during call
    public void sendDtmf(String json) {
        try {
            JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
            String digit = msg.get("digit").getAsString();
            // Send via AudioManager or TelecomManager
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            // DTMF via in-call tones
            Log.d(TAG, "DTMF: " + digit);
        } catch (Exception e) {
            Log.e(TAG, "sendDtmf: " + e.getMessage());
        }
    }

    private void endCallViaReflection() throws Exception {
        TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (tm == null) return;
        Method endCall = tm.getClass().getMethod("endCall");
        endCall.invoke(tm);
    }
}
