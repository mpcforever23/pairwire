package com.darkengine.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DarkEngine live mode. Runs in the background with one quiet notification and checks the
 * linked MT5 accounts every few seconds, so trade, news and news-result alerts arrive at once
 * instead of every 15 minutes.
 */
public class DarkWatchService extends Service {
    public static final String PREFS = "darkwatch";
    public static final String CH_LIVE = "de_live";
    public static final String CH_ALERT = "de_alerts";
    static final int FG_ID = 4242;
    static final long RECENT_MS = 6L * 3600 * 1000;
    public static volatile boolean running = false;

    private ScheduledExecutorService ex;
    private String idTok = "";
    private long idExp = 0;
    private String fgText = "";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        channels(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        startFg("Starting live alerts…");
        if (!sp.getBoolean("on", false)) { stopForegroundCompat(); stopSelf(); return START_NOT_STICKY; }
        running = true;
        int every = 10;
        try { every = new JSONObject(sp.getString("cfg", "{}")).optInt("interval", 10); } catch (Exception e) { }
        every = Math.max(3, Math.min(120, every));
        if (ex != null) ex.shutdownNow();
        ex = Executors.newSingleThreadScheduledExecutor();
        ex.scheduleWithFixedDelay(this::safeCheck, 0, every, TimeUnit.SECONDS);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        if (ex != null) ex.shutdownNow();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel live = new NotificationChannel(CH_LIVE, "DarkEngine live mode", NotificationManager.IMPORTANCE_MIN);
        live.setDescription("Keeps instant alerts running. You can hide this one.");
        live.setShowBadge(false);
        nm.createNotificationChannel(live);
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            NotificationChannel a = new NotificationChannel(CH_ALERT, "DarkEngine alerts", NotificationManager.IMPORTANCE_HIGH);
            a.setDescription("New trades opened by DarkEngine, upcoming news and news results");
            a.enableVibration(true);
            a.enableLights(true);
            a.setLightColor(0xFFFF6A1A);
            a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(a);
        }
    }

    private int icon() {
        int id = getResources().getIdentifier("ic_stat_de", "drawable", getPackageName());
        return id != 0 ? id : android.R.drawable.ic_dialog_info;
    }

    private PendingIntent openApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (i == null) i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification fgNote(String text) {
        return new NotificationCompat.Builder(this, CH_LIVE)
                .setSmallIcon(icon())
                .setColor(0xFFFF6A1A)
                .setContentTitle("DarkEngine live alerts on")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setContentIntent(openApp())
                .build();
    }

    private void startFg(String text) {
        fgText = text;
        Notification n = fgNote(text);
        if (Build.VERSION.SDK_INT >= 34) startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(FG_ID, n);
    }

    private void setFg(String text) {
        if (text.equals(fgText)) return;
        fgText = text;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(FG_ID, fgNote(text));
    }

    private void alert(int id, String title, String body) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification n = new NotificationCompat.Builder(this, CH_ALERT)
                .setSmallIcon(icon())
                .setColor(0xFFFF6A1A)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        nm.notify(id, n);
    }

    // same hash as the app and the background check
    static int hid(String s) {
        long h = 5381;
        for (int i = 0; i < s.length(); i++) h = ((h * 33) ^ s.charAt(i)) & 0xFFFFFFFFL;
        return (int) (h % 2000000000L) + 1;
    }

    private static String num(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        }
        return String.valueOf(o);
    }

    private static String fmt(double v, String unit) {
        String s = java.math.BigDecimal.valueOf(Math.round(v * 100) / 100.0).stripTrailingZeros().toPlainString();
        return s + ("%".equals(unit) ? "%" : "k".equals(unit) ? "K" : "");
    }

    private static String http(String method, String url, String auth, String ctype, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        if (auth != null) c.setRequestProperty("Authorization", "Bearer " + auth);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", ctype);
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    private String token(JSONObject cfg, SharedPreferences sp) throws Exception {
        if (!idTok.isEmpty() && System.currentTimeMillis() < idExp) return idTok;
        String rt = cfg.getString("refreshToken");
        String r = http("POST", "https://securetoken.googleapis.com/v1/token?key=" + cfg.getString("apiKey"), null,
                "application/x-www-form-urlencoded", "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(rt, "UTF-8"));
        JSONObject j = new JSONObject(r);
        idTok = j.getString("id_token");
        idExp = System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 120) * 1000L;
        String nrt = j.optString("refresh_token", rt);
        if (!nrt.equals(rt)) { cfg.put("refreshToken", nrt); sp.edit().putString("cfg", cfg.toString()).apply(); }
        return idTok;
    }

    private void safeCheck() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            check(sp);
            sp.edit().putLong("last", System.currentTimeMillis()).putString("err", "").apply();
        } catch (Throwable e) {
            sp.edit().putString("err", String.valueOf(e.getMessage())).apply();
            setFg("Waiting for the internet…");
        }
    }

    // Unemployment is the one line where a lower number is hawkish (dollar-positive)
    private static String[] verdict(String name, double fc, double act) {
        String u = name.toUpperCase(Locale.US);
        double tol = u.contains("NONFARM") || u.contains("NON-FARM") ? 25 : u.contains("RATE") && (u.contains("FED") || u.contains("FOMC") || u.contains("FUNDS")) ? 0.01 : 0.05;
        int dir = u.contains("UNEMPLOYMENT") ? -1 : 1;
        double d = (act - fc) * dir;
        if (d > tol) return new String[]{"HOT", "Hotter than expected: dollar up, gold and EUR/GBP usually down."};
        if (d < -tol) return new String[]{"COOL", "Cooler than expected: dollar down, gold and EUR/GBP usually up."};
        return new String[]{"IN LINE", "In line with the forecast: expect a two-way spike that fades. Usually no trade."};
    }

    private void check(SharedPreferences sp) throws Exception {
        if (!sp.getBoolean("on", false)) { stopForegroundCompat(); stopSelf(); return; }
        JSONObject cfg = new JSONObject(sp.getString("cfg", "{}"));
        if (!cfg.has("uid") || !cfg.has("refreshToken")) { setFg("Sign in to the app to start"); return; }
        JSONObject prefs = cfg.optJSONObject("prefs");
        if (prefs == null) prefs = new JSONObject();
        boolean pTrades = prefs.optBoolean("trades", true), pNews = prefs.optBoolean("news", true), pRes = prefs.optBoolean("results", true);
        long lead = Math.max(1, prefs.optLong("lead", 15));
        String tok = token(cfg, sp);
        String body = http("GET", "https://firestore.googleapis.com/v1/projects/" + cfg.getString("project") + "/databases/(default)/documents/users/"
                + cfg.getString("uid") + "/accounts?pageSize=50", tok, null, null);
        JSONArray docs = new JSONObject(body).optJSONArray("documents");
        Set<Integer> seen = new LinkedHashSet<>();
        for (String x : sp.getString("seen", "").split(",")) { try { if (!x.isEmpty()) seen.add(Integer.parseInt(x)); } catch (Exception e) { } }
        boolean first = !sp.getBoolean("init", false);
        long now = System.currentTimeMillis();
        int accN = 0, open = 0;
        List<Object[]> out = new ArrayList<>();
        if (docs != null) for (int i = 0; i < docs.length(); i++) {
            JSONObject acc;
            try { acc = new JSONObject(docs.getJSONObject(i).getJSONObject("fields").getJSONObject("json").getString("stringValue")); } catch (Exception e) { continue; }
            accN++;
            int tz = acc.optJSONObject("pnl") != null ? acc.getJSONObject("pnl").optInt("tz", 3) : 3;
            String login = num(acc.opt("login"));
            JSONArray pos = acc.optJSONArray("positions");
            if (pos != null) for (int k = 0; k < pos.length(); k++) {
                JSONObject p = pos.getJSONObject(k);
                if (!p.has("id")) continue;
                open++;
                int id = hid("pos:" + login + ":" + num(p.opt("id")));
                if (seen.contains(id)) continue;
                seen.add(id);
                long t = p.optLong("t", 0) * 1000L;
                if (first || !pTrades || t == 0 || now - t > RECENT_MS) continue;
                String op = num(p.opt("open"));
                String sl = p.optDouble("sl", 0) != 0 ? num(p.opt("sl")) : "none", tp = p.optDouble("tp", 0) != 0 ? num(p.opt("tp")) : "none";
                String side = "buy".equals(p.optString("side")) ? "BUY" : "SELL";
                String strat = p.optString("strat", "");
                out.add(new Object[]{id, "DarkEngine " + side + " " + p.optString("sym"),
                        String.format(Locale.US, "%.2f", p.optDouble("vol", 0)) + " lots at " + op + " · SL " + sl + " · TP " + tp + (strat.isEmpty() ? "" : " · " + strat)});
            }
            JSONArray news = acc.optJSONArray("news");
            if (news != null) for (int k = 0; k < news.length(); k++) {
                JSONObject n = news.getJSONObject(k);
                long tS = n.optLong("t", 0), t = tS * 1000L;
                String name = n.optString("name"), cur = n.optString("cur", "USD"), fam = n.optString("fam", cur);
                // coming up
                if (pNews && t > now && t - now <= lead * 60000L) {
                    int id = hid("news:" + tS + ":" + cur + ":" + name);
                    if (!seen.contains(id)) {
                        seen.add(id);
                        long mins = Math.max(1, Math.round((t - now) / 60000.0));
                        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
                        f.setTimeZone(TimeZone.getTimeZone("GMT" + (tz >= 0 ? "+" : "-") + Math.abs(tz)));
                        String fc = n.has("fc") ? " Forecast " + fmt(n.getDouble("fc"), n.optString("u")) + "." : "";
                        out.add(new Object[]{id, fam + " in " + mins + " min: " + name,
                                "At " + f.format(new Date(t)) + (tz == 3 ? " EAT" : "") + "." + fc + " DarkEngine pauses new USD trades 30 min before and after."});
                    }
                }
                // just released
                if (pRes && t <= now && now - t < 3L * 3600 * 1000 && n.has("act")) {
                    int id = hid("res:" + tS + ":" + cur + ":" + name);
                    if (!seen.contains(id)) {
                        seen.add(id);
                        if (!first || now - t < 15 * 60000L) {
                            String u = n.optString("u");
                            double act = n.getDouble("act");
                            String line = "Actual " + fmt(act, u) + (n.has("fc") ? " vs forecast " + fmt(n.getDouble("fc"), u) : "") + (n.has("prev") ? " (previous " + fmt(n.getDouble("prev"), u) + ")" : "") + ".";
                            String title = fam + " out: " + name, more = "";
                            if (n.has("fc")) { String[] v = verdict(name, n.getDouble("fc"), act); title = fam + " " + v[0] + ": " + name; more = " " + v[1]; }
                            out.add(new Object[]{id, title, line + more + " Open DarkEngine › News for the full read."});
                        }
                    }
                }
            }
        }
        for (int k = 0; k < out.size() && k < 8; k++) { Object[] o = out.get(k); alert((Integer) o[0], (String) o[1], (String) o[2]); }
        List<Integer> keep = new ArrayList<>(seen);
        StringBuilder sb = new StringBuilder();
        for (int k = Math.max(0, keep.size() - 800); k < keep.size(); k++) { if (sb.length() > 0) sb.append(','); sb.append(keep.get(k)); }
        sp.edit().putString("seen", sb.toString()).putBoolean("init", true).apply();
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.getDefault());
        setFg(accN == 0 ? "No MT5 account linked yet" : "Watching " + accN + " account" + (accN == 1 ? "" : "s") + " · " + open + " open · checked " + hm.format(new Date()));
    }
}
