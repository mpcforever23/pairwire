# Pairwire for Android

Gold and dollar-pair reactions to US news releases, with forecast vs actual labels and Buy/Sell reads.

## Get the APK (about 10 minutes, free)

1. Sign in at https://github.com (create a free account if you don't have one).
2. Click **+** (top right) → **New repository**. Name it `pairwire`, keep it **Private**, and click **Create repository**.
3. On the new repo page, click **uploading an existing file**.
4. Unzip `pairwire-android.zip` on your computer, open the `pairwire-android` folder, select **everything inside it** (including the `.github` folder) and drag it into the GitHub page. Click **Commit changes**.
5. Open the **Actions** tab. The job "Build Pairwire APK" starts by itself. Wait until it shows a green tick (5–10 minutes).
6. Click the finished run, scroll to **Artifacts**, and download **Pairwire-APK**. Unzip it to get `Pairwire.apk`.
7. Send `Pairwire.apk` to your phone, open it, and allow "Install unknown apps" when Android asks.

**If the Actions tab shows no job:** the `.github` folder did not upload. Click **Add file → Create new file**, type the name `.github/workflows/build-apk.yml`, paste the contents of that file from the zip, and commit.

## Updating the app
Replace `www/index.html` with a new version and commit. GitHub builds a new APK automatically.
