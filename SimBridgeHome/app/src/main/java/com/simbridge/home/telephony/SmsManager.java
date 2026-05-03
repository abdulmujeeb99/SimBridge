package com.simbridge.home.telephony;

import android.content.Context;
import android.util.Log;

import com.simbridge.home.services.SignalingService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SmsManager {

    private static final String TAG = "SmsManager";
    private final Context context;
    private final SignalingService signalingService;

    public SmsManager(Context context, SignalingService signalingService) {
        this.context = context;
        this.signalingService = signalingService;
    }

    // Called by SmsReceiver when a real SMS arrives on home SIM
    public static void onSmsReceived(Context context, String sender, String body, long timestamp) {
        Log.d(TAG, "SMS from: " + sender + " body: " + body);
        // This will be forwarded by RelayService via SignalingService
        android.content.Intent broadcast = new android.content.Intent("simbridge.TELEPHONY_SMS");
        broadcast.putExtra("sender", sender);
        broadcast.putExtra("body", body);
        broadcast.putExtra("timestamp", timestamp);
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(context).sendBroadcast(broadcast);
    }

    // Travel device wants to send an SMS via home SIM
    public void sendSms(String json) {
        try {
            JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
            String to = msg.get("to").getAsString();
            String body = msg.get("body").getAsString();
            Log.d(TAG, "Sending SMS to: " + to);

            android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
            smsManager.sendTextMessage(to, null, body, null, null);
            signalingService.sendSmsSent(to, true);
        } catch (Exception e) {
            Log.e(TAG, "sendSms error: " + e.getMessage());
        }
    }
}
