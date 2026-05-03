package com.simbridge.home.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.simbridge.home.services.RelayService;
import com.simbridge.home.utils.AppConfig;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!AppConfig.isSetupDone(context)) return;

        Log.d("BootReceiver", "Boot complete — starting RelayService");
        Intent service = new Intent(context, RelayService.class);
        context.startForegroundService(service);
    }
}
