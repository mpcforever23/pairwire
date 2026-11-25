# DarkEngine for Android

The phone app for the **DarkVision V3.0** MT5 robot: day, week and month results (closed at 23:00 East Africa Time), every trade the robot opens, news that pauses trading, world news, PDF reports, and alerts at the top of the phone.

Every push to `main` builds a new APK. Open the **Actions** tab, click the latest green run, and download **DarkEngine-APK** from the Artifacts section.

## How it gets data
The DarkVision V3.0 EA sends each account's results to the app's Firebase database. Firebase settings live in `www/index.html` (`const FB = {...}`) and in the EA inputs.

## Alerts
- With the app open, new trades and news alerts arrive within seconds.
- With the app closed, `www/runners/background.js` runs about every 15 minutes (Android's minimum) and posts alerts for new trades and for news coming up.
- Set Battery for DarkEngine to **Unrestricted** for the most reliable background checks.

## Files
- `www/index.html`: the whole app
- `www/runners/background.js`: the background check
- `assets/`: app icon and splash (DarkEngine gear)
- `android-res/`: status-bar icon and the script that adds it, the notification permissions and the runner library to the Android project
