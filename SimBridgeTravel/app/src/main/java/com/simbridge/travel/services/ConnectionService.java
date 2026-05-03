package com.simbridge.travel.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.travel.activities.IncomingCallActivity;
import com.simbridge.travel.activities.MainActivity;
import com.simbridge.travel.utils.AppConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class ConnectionService extends Service {

    private static final String TAG = "ConnectionService";
    private static final String CHANNEL_ID = "simbridge_travel_channel";
    private static final int NOTIF_ID = 2001;

    // Broadcast actions
    public static final String ACTION_HOME_CONNECTED    = "travel.HOME_CONNECTED";
    public static final String ACTION_HOME_DISCONNECTED = "travel.HOME_DISCONNECTED";
    public static final String ACTION_CALL_INCOMING     = "travel.CALL_INCOMING";
    public static final String ACTION_CALL_ENDED        = "travel.CALL_ENDED";
    public static final String ACTION_SMS_INCOMING      = "travel.SMS_INCOMING";
    public static final String ACTION_SMS_SENT          = "travel.SMS_SENT";
    public static final String ACTION_AUDIO_CHUNK       = "travel.AUDIO_CHUNK";
    public static final String EXTRA_DATA               = "data";

    // Compat aliases used by older activities
    public static final String ACTION_PAIRED            = ACTION_HOME_CONNECTED;
    public static final String ACTION_PEER_DISCONNECTED = ACTION_HOME_DISCONNECTED;

    private final IBinder binder = new LocalBinder();
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean connected = false;

    // Track all paired home devices: deviceId -> simLabel
    private final Map<String, String> pairedHomes = new ConcurrentHashMap<>();

    public class LocalBinder extends Binder {
        public ConnectionService getService() { return ConnectionService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Connecting..."));
    }

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
                updateNotification("Connected — waiting for Home devices...");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WS failure: " + t.getMessage());
                connected = false;
                pairedHomes.clear();
                updateNotification("Disconnected — retrying...");
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> connect(), 5000);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                connected = false;
            }
        });
    }

    private void register() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",     "register");
        msg.addProperty("deviceId", AppConfig.getDeviceId(this));
        msg.addProperty("role",     "travel");
        msg.addProperty("pairCode", AppConfig.getPairCode(this));
        send(msg.toString());
    }

    private void handleMessage(String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            String type = msg.get("type").getAsString();

            switch (type) {

                case "home_connected":
                case "paired": {
                    // A new home device connected or initial pair confirmation
                    // "paired" may have a "homes" array (initial list)
                    // "home_connected" has deviceId + simLabel
                    if (msg.has("homes")) {
                        // Initial list of already-connected homes
                        for (com.google.gson.JsonElement el : msg.get("homes").getAsJsonArray()) {
                            JsonObject h = el.getAsJsonObject();
                            String did   = h.get("deviceId").getAsString();
                            String label = h.get("simLabel").getAsString();
                            pairedHomes.put(did, label);
                        }
                    } else if (msg.has("deviceId")) {
                        String did   = msg.get("deviceId").getAsString();
                        String label = msg.has("simLabel") ? msg.get("simLabel").getAsString() : "SIM";
                        pairedHomes.put(did, label);
                    }
                    updateNotification("Paired with " + pairedHomes.size() + " home device(s) ✓");
                    broadcast(ACTION_HOME_CONNECTED, text);
                    break;
                }

                case "home_disconnected":
                case "peer_disconnected": {
                    if (msg.has("deviceId")) {
                        String did = msg.get("deviceId").getAsString();
                        pairedHomes.remove(did);
                    }
                    updateNotification(pairedHomes.isEmpty()
                            ? "No home devices connected"
                            : "Paired with " + pairedHomes.size() + " home device(s) ✓");
                    broadcast(ACTION_HOME_DISCONNECTED, text);
                    break;
                }

                case "call_incoming":
                    broadcast(ACTION_CALL_INCOMING, text);
                    showIncomingCallScreen(text);
                    break;

                case "call_ended":
                    broadcast(ACTION_CALL_ENDED, text);
                    break;

                case "sms_incoming":
                    broadcast(ACTION_SMS_INCOMING, text);
                    break;

                case "sms_sent":
                    broadcast(ACTION_SMS_SENT, text);
                    break;

                case "audio_chunk":
                    broadcast(ACTION_AUDIO_CHUNK, text);
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

    private void showIncomingCallScreen(String data) {
        try {
            JsonObject msg     = JsonParser.parseString(data).getAsJsonObject();
            String number      = msg.get("callerNumber").getAsString();
            String simLabel    = msg.has("simLabel")    ? msg.get("simLabel").getAsString()    : "SIM";
            String homeDeviceId= msg.has("homeDeviceId")? msg.get("homeDeviceId").getAsString(): "";

            Intent intent = new Intent(this, IncomingCallActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("callerNumber",  number);
            intent.putExtra("simLabel",      simLabel);
            intent.putExtra("homeDeviceId",  homeDeviceId);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "showIncomingCallScreen: " + e.getMessage());
        }
    }

    // ── Outbound send helpers ──────────────────────────────────────────────

    public void send(String json) {
        if (webSocket != null && connected) webSocket.send(json);
    }

    /** Place an outgoing call via a specific home device (by simLabel selection) */
    public void sendCallOutgoing(String number, String targetHomeId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "call_outgoing");
        msg.addProperty("number",       number);
        msg.addProperty("targetHomeId", targetHomeId);
        send(msg.toString());
    }

    public void sendCallAnswered(String targetHomeId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "call_answered");
        msg.addProperty("targetHomeId", targetHomeId);
        send(msg.toString());
    }

    public void sendCallRejected(String targetHomeId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "call_rejected");
        msg.addProperty("targetHomeId", targetHomeId);
        send(msg.toString());
    }

    public void sendCallEnded(String targetHomeId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "call_ended");
        msg.addProperty("targetHomeId", targetHomeId);
        send(msg.toString());
    }

    public void sendSms(String to, String body, String targetHomeId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "sms_send");
        msg.addProperty("to",           to);
        msg.addProperty("body",         body);
        msg.addProperty("targetHomeId", targetHomeId);
        send(msg.toString());
    }

    public void sendDtmf(String digit, String targetHomeId) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type",         "dtmf");
        msg.addProperty("digit",        digit);
        msg.addProperty("targetHomeId", targetHomeId);
        send(msg.toString());
    }

    // ── Home device info accessors ─────────────────────────────────────────

    public Map<String, String> getPairedHomes() { return pairedHomes; }

    /** Returns list of "simLabel|deviceId" strings for spinner/dropdown */
    public List<String[]> getHomeList() {
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, String> e : pairedHomes.entrySet()) {
            list.add(new String[]{ e.getValue(), e.getKey() }); // [label, deviceId]
        }
        return list;
    }

    public boolean isConnected() { return connected; }
    public boolean isPaired()    { return !pairedHomes.isEmpty(); }

    // ── Notification ───────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "SimBridge Travel", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SimBridge Travel")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(text));
    }

    private void broadcast(String action, String data) {
        Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        if (webSocket != null) webSocket.close(1000, "stopped");
        super.onDestroy();
    }
}
