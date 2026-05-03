package com.simbridge.home.utils;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

public class AppConfig {

    public static final String PREFS_NAME = "simbridge_prefs";
    public static final String DEFAULT_SERVER_URL = "ws://YOUR_SERVER_IP:8080";

    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_PAIR_CODE  = "pair_code";
    private static final String KEY_DEVICE_ID  = "device_id";
    private static final String KEY_SETUP_DONE = "setup_done";
    private static final String KEY_SIM_LABEL  = "sim_label";

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
        String code = prefs(ctx).getString(KEY_PAIR_CODE, null);
        if (code == null) {
            code = String.valueOf((int)(Math.random() * 900000) + 100000);
            prefs(ctx).edit().putString(KEY_PAIR_CODE, code).apply();
        }
        return code;
    }
    public static void setPairCode(Context ctx, String code) {
        prefs(ctx).edit().putString(KEY_PAIR_CODE, code).apply();
    }

    public static String getDeviceId(Context ctx) {
        String id = prefs(ctx).getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = "home_" + UUID.randomUUID().toString().substring(0, 8);
            prefs(ctx).edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    /** Human label for this SIM shown on travel phone e.g. "Karachi SIM" */
    public static String getSimLabel(Context ctx) {
        return prefs(ctx).getString(KEY_SIM_LABEL, "Home SIM");
    }
    public static void setSimLabel(Context ctx, String label) {
        prefs(ctx).edit().putString(KEY_SIM_LABEL, label).apply();
    }

    public static boolean isSetupDone(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SETUP_DONE, false);
    }
    public static void setSetupDone(Context ctx, boolean done) {
        prefs(ctx).edit().putBoolean(KEY_SETUP_DONE, done).apply();
    }
}
