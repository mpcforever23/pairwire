# DarkEngine for Android

The phone app for the **DarkEngine V2.1** MT5 robot: day, week and month results (closed at 23:00 East Africa Time), every trade the robot opens, US NFP / CPI / PPI / FOMC with a full trade analysis, world news, PDF reports, and instant alerts.

Every push to `main` builds a new APK. Open the **Actions** tab, click the latest green run, and download **DarkEngine-APK** from the Artifacts section.

## How it gets data
The DarkEngine V2.1 EA sends each account's results to the app's Firebase database. Firebase settings live in `www/index.html` (`const FB = {...}`) and in the EA inputs.

## Alerts
- **Live mode** (on by default): `android-res/java/DarkWatchService.java` runs in the background with one quiet notification and checks the accounts every 5, 10 or 30 seconds. New trades, upcoming news and news results (actual vs forecast) arrive within seconds, app open or closed. It restarts after a phone restart.
- The EA sends to the app at once when a trade opens or closes and when a news figure is released.
- With live mode off, `www/runners/background.js` runs about every 15 minutes (Android's minimum).
- Set Battery for DarkEngine to **Unrestricted** (the app has a button for it) so Android does not pause live mode.

## News analysis
The EA sends US NFP, CPI, PPI and FOMC from the broker's MT5 calendar (last 40 days, next 14) with forecast, actual and previous. The News tab fills the figures by itself and adds a surprise meter, a scored trade decision with reasons, the market going into the print, the track record of each call, a risk plan and release rules.

## Files
- `www/index.html`: the whole app
- `www/runners/background.js`: the 15-minute background check (fallback)
- `android-res/java/`: live mode service, its plugin and boot receiver
- `assets/`: app icon and splash (DarkEngine gear)
- `android-res/`: status-bar icon and the script that adds it, the notification permissions and the runner library to the Android project
