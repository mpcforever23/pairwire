# EagleTracker for Android

Live tracker for the **EagleReversion V2.0** MT5 EA: results, open positions, trade history, statistics, the EA's news shield, news predictions and world news.

Every push to `main` builds a new APK. Open the **Actions** tab, click the latest green run, and download **EagleTracker-APK** from the Artifacts section.

## How the data gets here
- Each chart running EagleReversion signs in with the app email/password (EA inputs) and writes one document to Firebase: `users/{uid}/accounts/ER-{login}-{symbol}`.
- The app reads only documents marked `ea: "EagleReversion"` and merges the charts of the same MT5 login. Manual trades and other robots are never shown, because the EA only reports positions opened with its own magic number.
- MT5 needs these URLs in Tools > Options > Expert Advisors > Allow WebRequest: `https://identitytoolkit.googleapis.com`, `https://firestore.googleapis.com`, `https://api.telegram.org`.

Firebase settings live at the top of the EagleTracker section in `www/index.html` (`const FB = {...}`).
