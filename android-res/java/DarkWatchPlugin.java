package com.darkengine.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Starts and stops DarkEngine live mode from the web app. */
@CapacitorPlugin(name = "DarkWatch")
public class DarkWatchPlugin extends Plugin {

    @PluginMethod
    public void start(PluginCall call) {
        Context c = getContext();
        JSObject d = call.getData();
        SharedPreferences sp = c.getSharedPreferences(DarkWatchService.PREFS, Context.MODE_PRIVATE);
        String old = sp.getString("cfg", "{}");
        boolean sameUser = old.contains("\"uid\":\"" + d.getString("uid", "") + "\"");
        SharedPreferences.Editor e = sp.edit().putString("cfg", d.toString()).putBoolean("on", true);
        if (!sameUser) e.putBoolean("init", false).putString("seen", "");
        e.apply();
        try {
            Intent i = new Intent(c, DarkWatchService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
            call.resolve(status(c));
        } catch (Exception ex) {
            call.reject("Could not start live alerts: " + ex.getMessage());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Context c = getContext();
        c.getSharedPreferences(DarkWatchService.PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", false).apply();
        c.stopService(new Intent(c, DarkWatchService.class));
        call.resolve(status(c));
    }

    @PluginMethod
    public void status(PluginCall call) { call.resolve(status(getContext())); }

    @SuppressLint("BatteryLife")
    @PluginMethod
    public void askBattery(PluginCall call) {
        Context c = getContext();
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 23 && !ignoring(c)) {
                i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + c.getPackageName()));
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ex) {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception ignored) { }
        }
        call.resolve(status(c));
    }

    // opens Android's settings for the live-mode notification only, so it can be switched off
    @PluginMethod
    public void hideNote(PluginCall call) {
        Context c = getContext();
        DarkWatchService.channels(c);
        try {
            Intent i;
            if (Build.VERSION.SDK_INT >= 26) {
                i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
                i.putExtra(Settings.EXTRA_CHANNEL_ID, DarkWatchService.CH_LIVE);
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ex) { }
        call.resolve(status(c));
    }

    static boolean noteHidden(Context c) {
        if (Build.VERSION.SDK_INT < 26) return false;
        android.app.NotificationManager nm = (android.app.NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        android.app.NotificationChannel ch = nm == null ? null : nm.getNotificationChannel(DarkWatchService.CH_LIVE);
        return ch != null && ch.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE;
    }

    static boolean ignoring(Context c) {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    static JSObject status(Context c) {
        SharedPreferences sp = c.getSharedPreferences(DarkWatchService.PREFS, Context.MODE_PRIVATE);
        JSObject o = new JSObject();
        o.put("on", sp.getBoolean("on", false));
        o.put("running", DarkWatchService.running);
        o.put("battery", ignoring(c));
        o.put("last", sp.getLong("last", 0));
        o.put("err", sp.getString("err", ""));
        o.put("state", sp.getString("state", ""));
        o.put("hidden", noteHidden(c));
        return o;
    }
}
