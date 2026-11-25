// DarkEngine background check. Android runs this about every 15 minutes while the app is closed.
// It is the fallback for when live mode is off: with live mode on, the DarkWatch service checks every
// few seconds instead and this check does nothing. It posts an alert for each new DarkEngine trade,
// for news that is about to be released and for a news figure that has just come out.
// Each event gets a fresh context, so state lives in CapacitorKV.

const RECENT_MS = 6 * 3600 * 1000;
const CHANNEL = "de_alerts";
const ICON = "ic_stat_de";

// same hash as the app, so a trade or news item is never announced twice
function hid(s) { let h = 5381; for (let i = 0; i < s.length; i++) h = ((h * 33) ^ s.charCodeAt(i)) >>> 0; return (h % 2000000000) + 1; }
function kvGet(k, d) { try { const r = CapacitorKV.get(k); const v = r && r.value; return v ? JSON.parse(v) : d; } catch (e) { return d; } }
function kvSet(k, v) { CapacitorKV.set(k, JSON.stringify(v)); }
function pad(n) { return (n < 10 ? "0" : "") + n; }
function timeIn(sec, tz) { const d = new Date((sec + tz * 3600) * 1000); return pad(d.getUTCHours()) + ":" + pad(d.getUTCMinutes()); }
function digits(x) { const s = String(x).split(".")[1]; return s ? s.length : 2; }
function fmtV(v, u) { return (Math.round(v * 100) / 100) + (u === "%" ? "%" : u === "k" ? "K" : ""); }
// unemployment is the one line where a lower number is hawkish (dollar-positive)
function verdict(name, fc, act) {
  const u = name.toUpperCase();
  const tol = /NONFARM|NON-FARM/.test(u) ? 25 : /RATE/.test(u) && /FED|FOMC|FUNDS/.test(u) ? 0.01 : 0.05, dir = /UNEMPLOYMENT/.test(u) ? -1 : 1;
  const d = (act - fc) * dir;
  return d > tol ? ["HOT", "Hotter than expected: dollar up, gold and EUR/GBP usually down."] : d < -tol ? ["COOL", "Cooler than expected: dollar down, gold and EUR/GBP usually up."] : ["IN LINE", "In line with the forecast: expect a two-way spike that fades. Usually no trade."];
}

async function idToken(auth) {
  const r = await fetch("https://securetoken.googleapis.com/v1/token?key=" + auth.apiKey, {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=refresh_token&refresh_token=" + encodeURIComponent(auth.refreshToken),
  });
  const j = await r.json();
  if (!j || !j.id_token) throw new Error("token");
  if (j.refresh_token && j.refresh_token !== auth.refreshToken) { auth.refreshToken = j.refresh_token; kvSet("auth", auth); }
  return j.id_token;
}

async function check() {
  const auth = kvGet("auth", null);
  if (!auth || !auth.uid || !auth.refreshToken || auth.live) return;
  const prefs = auth.prefs || { trades: true, news: true, results: true, lead: 15 };
  const lead = Number(prefs.lead) || 15;
  const tok = await idToken(auth);
  const r = await fetch("https://firestore.googleapis.com/v1/projects/" + auth.project + "/databases/(default)/documents/users/" + auth.uid + "/accounts?pageSize=50", {
    method: "GET", headers: { Authorization: "Bearer " + tok },
  });
  const j = await r.json();
  const docs = (j && j.documents) || [];
  const seen = new Set(kvGet("seen", []));
  const first = !kvGet("init", false);
  const now = Date.now();
  const out = [];
  for (let i = 0; i < docs.length; i++) {
    let acc;
    try { acc = JSON.parse(docs[i].fields.json.stringValue); } catch (e) { continue; }
    const tz = acc.pnl && isFinite(acc.pnl.tz) ? Number(acc.pnl.tz) : 3;
    const pos = acc.positions || [];
    for (let k = 0; k < pos.length; k++) {
      const p = pos[k];
      if (p.id == null) continue;
      const id = hid("pos:" + acc.login + ":" + p.id);
      if (seen.has(id)) continue;
      seen.add(id);
      if (first || prefs.trades === false || !p.t || now - p.t * 1000 > RECENT_MS) continue;
      const d = digits(p.open), f = v => v ? Number(v).toFixed(d) : "none";
      out.push({ id, title: "DarkEngine " + (p.side === "buy" ? "BUY" : "SELL") + " " + p.sym,
        body: Number(p.vol).toFixed(2) + " lots at " + f(p.open) + " · SL " + f(p.sl) + " · TP " + f(p.tp),
        channelId: CHANNEL, smallIcon: ICON, autoCancel: true });
    }
    const news = acc.news || [];
    for (let k = 0; k < news.length; k++) {
      const n = news[k], t = n.t * 1000, fam = n.fam || n.cur;
      if (prefs.results !== false && n.act != null && t <= now && now - t < 3 * 3600000) {
        const rid = hid("res:" + n.t + ":" + n.cur + ":" + n.name);
        if (!seen.has(rid)) {
          seen.add(rid);
          if (!first) {
            const v = n.fc != null ? verdict(n.name, n.fc, n.act) : null;
            out.push({ id: rid, title: fam + " " + (v ? v[0] : "out") + ": " + n.name,
              body: "Actual " + fmtV(n.act, n.u) + (n.fc != null ? " vs forecast " + fmtV(n.fc, n.u) : "") + ". " + (v ? v[1] : ""),
              channelId: CHANNEL, smallIcon: ICON, autoCancel: true });
          }
        }
      }
      if (prefs.news === false) continue;
      const id = hid("news:" + n.t + ":" + n.cur + ":" + n.name);
      if (seen.has(id) || t <= now || now < t - lead * 60000) continue;
      seen.add(id);
      const mins = Math.max(1, Math.round((t - now) / 60000));
      out.push({ id, title: fam + " in " + mins + " min: " + n.name,
        body: "At " + timeIn(n.t, tz) + (tz === 3 ? " EAT" : "") + "." + (n.fc != null ? " Forecast " + fmtV(n.fc, n.u) + "." : "") + " DarkEngine pauses new USD trades 30 min before and after.",
        channelId: CHANNEL, smallIcon: ICON, autoCancel: true });
    }
  }
  if (out.length) CapacitorNotifications.schedule(out.slice(0, 8));
  kvSet("seen", Array.from(seen).slice(-600));
  kvSet("init", true);
}

addEventListener("checkTrades", async (resolve, reject, args) => {
  try { await check(); } catch (e) { console.log("DarkEngine check failed: " + e); }
  resolve();
});

addEventListener("saveAuth", (resolve, reject, args) => {
  try { if (args && args.uid) kvSet("auth", args); } catch (e) {}
  resolve();
});

addEventListener("clearAuth", (resolve, reject, args) => {
  try { CapacitorKV.remove("auth"); } catch (e) {}
  resolve();
});

addEventListener("getState", (resolve, reject, args) => {
  resolve({ seen: kvGet("seen", []), signedIn: !!kvGet("auth", null) });
});

addEventListener("markSeen", (resolve, reject, args) => {
  try {
    const seen = new Set(kvGet("seen", []));
    const ids = (args && args.ids) || [];
    for (let i = 0; i < ids.length; i++) seen.add(Number(ids[i]));
    kvSet("seen", Array.from(seen).slice(-600));
    kvSet("init", true);
  } catch (e) {}
  resolve();
});
