package com.darkengine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Restarts live mode after the phone restarts or the app is updated. */
public class DarkWatchBoot extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!c.getSharedPreferences(DarkWatchService.PREFS, Context.MODE_PRIVATE).getBoolean("on", false)) return;
        try {
            Intent i = new Intent(c, DarkWatchService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }
}
