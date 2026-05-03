package com.simbridge.home.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        StringBuilder body = new StringBuilder();
        String sender = messages[0].getDisplayOriginatingAddress();
        long timestamp = messages[0].getTimestampMillis();

        for (SmsMessage msg : messages) {
            body.append(msg.getMessageBody());
        }

        SmsManager.onSmsReceived(context, sender, body.toString(), timestamp);
    }
}
