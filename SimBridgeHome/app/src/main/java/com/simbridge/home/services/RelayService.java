package com.simbridge.home.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.simbridge.home.activities.MainActivity;
import com.simbridge.home.telephony.CallManager;
import com.simbridge.home.telephony.SmsManager;

public class RelayService extends Service {

    private static final String TAG = "RelayService";
    private static final String CHANNEL_ID = "simbridge_channel";
    private static final int NOTIF_ID = 1001;

    private SignalingService signalingService;
    private boolean signalingBound = false;
    private PowerManager.WakeLock wakeLock;
    private CallManager callManager;
    private SmsManager smsManager;

    private final ServiceConnection signalingConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            signalingService = ((SignalingService.LocalBinder) service).getService();
            signalingBound = true;
            callManager = new CallManager(RelayService.this, signalingService);
            smsManager = new SmsManager(RelayService.this, signalingService);
            Log.d(TAG, "SignalingService bound");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            signalingBound = false;
        }
    };

    // Receive signaling events and act on them
    private final BroadcastReceiver signalingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String data = intent.getStringExtra(SignalingService.EXTRA_DATA);
            if (action == null) return;

            switch (action) {
                case SignalingService.ACTION_CALL_OUTGOING:
                    // Travel side wants to place a call
                    if (callManager != null) callManager.placeOutgoingCall(data);
                    break;
                case SignalingService.ACTION_CALL_ANSWERED:
                    // Travel side answered our incoming call notification
                    if (callManager != null) callManager.answerIncomingCall();
                    break;
                case SignalingService.ACTION_CALL_REJECTED:
                    if (callManager != null) callManager.rejectIncomingCall();
                    break;
                case SignalingService.ACTION_CALL_ENDED:
                    if (callManager != null) callManager.endCall();
                    break;
                case SignalingService.ACTION_SMS_SEND:
                    if (smsManager != null) smsManager.sendSms(data);
                    break;
                case SignalingService.ACTION_PAIRED:
                    updateNotification("Connected — Travel device paired ✓");
                    break;
                case SignalingService.ACTION_PEER_DISCONNECTED:
                    updateNotification("Active — Waiting for travel device...");
                    break;
                case SignalingService.ACTION_DTMF:
                    if (callManager != null) callManager.sendDtmf(data);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Starting SimBridge..."));
        acquireWakeLock();
        bindSignalingService();
        registerSignalingReceiver();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateNotification("Active — Waiting for travel device...");
        return START_STICKY;
    }

    private void bindSignalingService() {
        Intent intent = new Intent(this, SignalingService.class);
        startService(intent);
        bindService(intent, signalingConnection, Context.BIND_AUTO_CREATE);
    }

    private void registerSignalingReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SignalingService.ACTION_PAIRED);
        filter.addAction(SignalingService.ACTION_PEER_DISCONNECTED);
        filter.addAction(SignalingService.ACTION_CALL_OUTGOING);
        filter.addAction(SignalingService.ACTION_CALL_ANSWERED);
        filter.addAction(SignalingService.ACTION_CALL_REJECTED);
        filter.addAction(SignalingService.ACTION_CALL_ENDED);
        filter.addAction(SignalingService.ACTION_SMS_SEND);
        filter.addAction(SignalingService.ACTION_DTMF);
        LocalBroadcastManager.getInstance(this).registerReceiver(signalingReceiver, filter);
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimBridge:RelayLock");
        wakeLock.acquire();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "SimBridge Service", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("SimBridge relay running");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SimBridge Home")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    public void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        if (signalingBound) unbindService(signalingConnection);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(signalingReceiver);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
