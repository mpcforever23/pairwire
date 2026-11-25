# Run after `npx cap add android`: wires in the background runner library, the status-bar icon,
# the notification permissions and the live-alert service (android-res/java), and drops the
# location permissions the runner library declares.
import pathlib, re, shutil, glob
root = pathlib.Path("android/app")
g = root / "build.gradle"
s = g.read_text()
line = "dirs '../../node_modules/@capacitor/background-runner/android/src/main/libs', 'libs'"
if line not in s:
    s, n = re.subn(r"(flatDir\s*\{[^}]*?)(\n\s*\})", r"\1\n        " + line + r"\2", s, count=1)
    if n == 0:
        s += "\nrepositories {\n    flatDir {\n        " + line + "\n    }\n}\n"
    g.write_text(s)
d = root / "src/main/res/drawable"
d.mkdir(parents=True, exist_ok=True)
shutil.copy("android-res/ic_stat_de.png", d / "ic_stat_de.png")
m = root / "src/main/AndroidManifest.xml"
x = m.read_text()
if "xmlns:tools" not in x:
    x = x.replace('xmlns:android="http://schemas.android.com/apk/res/android"', 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools"', 1)
add = """
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" tools:node="remove" />
"""
if "ACCESS_FINE_LOCATION\" tools:node" not in x:
    x = x.replace("</manifest>", add + "</manifest>")
svc = """
        <service android:name=".DarkWatchService" android:exported="false" android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Live alerts for trades opened by the user's own MT5 robot" />
        </service>
        <receiver android:name=".DarkWatchBoot" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
"""
if "DarkWatchService" not in x:
    i = x.rfind("</application>")
    x = x[:i] + svc + "    " + x[i:]
m.write_text(x)
# live-alert service, its plugin and the activity that registers it
jdir = root / "src/main/java/com/darkengine/app"
jdir.mkdir(parents=True, exist_ok=True)
for f in glob.glob("android-res/java/*.java"):
    shutil.copy(f, jdir / pathlib.Path(f).name)
print("android patched:", sorted(p.name for p in jdir.glob("*.java")))
print(s[s.find("repositories"):s.find("repositories") + 300])
