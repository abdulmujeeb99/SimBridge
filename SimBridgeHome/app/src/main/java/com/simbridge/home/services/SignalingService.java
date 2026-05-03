package com.simbridge.home.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.home.utils.AppConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.concurrent.TimeUnit;

public class SignalingService extends Service {

    private static final String TAG = "SignalingService";

    public static final String ACTION_TRAVEL_CONNECTED    = "simbridge.TRAVEL_CONNECTED";
    public static final String ACTION_TRAVEL_DISCONNECTED = "simbridge.TRAVEL_DISCONNECTED";
    public static final String ACTION_CALL_OUTGOING       = "simbridge.CALL_OUTGOING";
    public static final String ACTION_CALL_INCOMING       = "simbridge.CALL_INCOMING";
    public static final String ACTION_CALL_ANSWERED       = "simbridge.CALL_ANSWERED";
    public static final String ACTION_CALL_REJECTED       = "simbridge.CALL_REJECTED";
    public static final String ACTION_CALL_ENDED          = "simbridge.CALL_ENDED";
    public static final String ACTION_SMS_SEND            = "simbridge.SMS_SEND";
    public static final String ACTION_DTMF                = "simbridge.DTMF";
    public static final String EXTRA_DATA                 = "data";

    // Keep backward compat aliases used by RelayService
    public static final String ACTION_PAIRED              = ACTION_TRAVEL_CONNECTED;
    public static final String ACTION_PEER_DISCONNECTED   = ACTION_TRAVEL_DISCONNECTED;
    public static final String ACTION_OFFER               = "simbridge.OFFER";
    public static final String ACTION_ANSWER              = "simbridge.ANSWER";
    public static final String ACTION_ICE                 = "simbridge.ICE";

    private final IBinder binder = new LocalBinder();
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean connected = false;
    private boolean travelConnected = false;

    public class LocalBinder extends Binder {
        public SignalingService getService() { return SignalingService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connect();
        return START_STICKY;
    }

    public void connect() {
        httpClient = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(AppConfig.getServerUrl(this)).build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                connected = true;
                register();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WS failure: " + t.getMessage());
                connected = false;
                travelConnected = false;
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> connect(), 5000);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                connected = false;
                travelConnected = false;
            }
        });
    }

    private void register() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",     "register");
        msg.addProperty("deviceId", AppConfig.getDeviceId(this));
        msg.addProperty("role",     "home");
        msg.addProperty("pairCode", AppConfig.getPairCode(this));
        msg.addProperty("simLabel", AppConfig.getSimLabel(this)); // e.g. "Karachi SIM"
        send(msg.toString());
    }

    private void handleMessage(String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            String type = msg.get("type").getAsString();
            switch (type) {
                case "paired":
                case "travel_connected":
                    travelConnected = true;
                    broadcast(ACTION_TRAVEL_CONNECTED, text);
                    break;
                case "peer_disconnected":
                case "travel_disconnected":
                    travelConnected = false;
                    broadcast(ACTION_TRAVEL_DISCONNECTED, text);
                    break;
                case "call_outgoing":
                    broadcast(ACTION_CALL_OUTGOING, text);
                    break;
                case "call_answered":
                    broadcast(ACTION_CALL_ANSWERED, text);
                    break;
                case "call_rejected":
                    broadcast(ACTION_CALL_REJECTED, text);
                    break;
                case "call_ended":
                    broadcast(ACTION_CALL_ENDED, text);
                    break;
                case "sms_send":
                    broadcast(ACTION_SMS_SEND, text);
                    break;
                case "dtmf":
                    broadcast(ACTION_DTMF, text);
                    break;
                case "audio_chunk":
                    broadcast("simbridge.AUDIO_CHUNK", text);
                    break;
                case "ping":
                    JsonObject pong = new JsonObject();
                    pong.addProperty("type", "pong");
                    send(pong.toString());
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "handleMessage: " + e.getMessage());
        }
    }

    public void send(String json) {
        if (webSocket != null && connected) webSocket.send(json);
    }

    // ── Outbound helpers called by RelayService / CallManager / SmsManager ──

    public void sendCallIncoming(String callerNumber, String callId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "call_incoming");
        msg.addProperty("callerNumber", callerNumber);
        msg.addProperty("callId",       callId);
        msg.addProperty("simLabel",     AppConfig.getSimLabel(this));
        msg.addProperty("homeDeviceId", AppConfig.getDeviceId(this));
        send(msg.toString());
    }

    public void sendCallEnded() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "call_ended");
        msg.addProperty("homeDeviceId", AppConfig.getDeviceId(this));
        send(msg.toString());
    }

    public void sendSmsIncoming(String sender, String body, long timestamp) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "sms_incoming");
        msg.addProperty("sender",       sender);
        msg.addProperty("body",         body);
        msg.addProperty("timestamp",    timestamp);
        msg.addProperty("simLabel",     AppConfig.getSimLabel(this));
        msg.addProperty("homeDeviceId", AppConfig.getDeviceId(this));
        send(msg.toString());
    }

    public void sendSmsSent(String to, boolean success) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "sms_sent");
        msg.addProperty("to",           to);
        msg.addProperty("success",      success);
        msg.addProperty("simLabel",     AppConfig.getSimLabel(this));
        send(msg.toString());
    }

    private void broadcast(String action, String data) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public boolean isConnected() { return connected; }
    public boolean isTravelConnected() { return travelConnected; }

    @Override
    public void onDestroy() {
        if (webSocket != null) webSocket.close(1000, "stopped");
        super.onDestroy();
    }
}
