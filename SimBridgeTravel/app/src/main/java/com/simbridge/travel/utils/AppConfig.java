package com.simbridge.travel.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class AppConfig {

    public static final String PREFS_NAME = "simbridge_travel_prefs";
    public static final String DEFAULT_SERVER_URL = "ws://YOUR_SERVER_IP:8080";

    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_PAIR_CODE  = "pair_code";
    private static final String KEY_DEVICE_ID  = "device_id";
    private static final String KEY_SETUP_DONE = "setup_done";

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static String getServerUrl(Context ctx) {
        return prefs(ctx).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }
    public static void setServerUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(KEY_SERVER_URL, url).apply();
    }

    public static String getPairCode(Context ctx) {
        return prefs(ctx).getString(KEY_PAIR_CODE, "");
    }
    public static void setPairCode(Context ctx, String code) {
        prefs(ctx).edit().putString(KEY_PAIR_CODE, code).apply();
    }

    public static String getDeviceId(Context ctx) {
        String id = prefs(ctx).getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = "travel_" + UUID.randomUUID().toString().substring(0, 8);
            prefs(ctx).edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static boolean isSetupDone(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SETUP_DONE, false);
    }
    public static void setSetupDone(Context ctx, boolean done) {
        prefs(ctx).edit().putBoolean(KEY_SETUP_DONE, done).apply();
    }
}
