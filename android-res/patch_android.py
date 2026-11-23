# Run after `npx cap add android`: wires in the background runner library, the status-bar icon
# and the notification permissions, and drops the location permissions the runner library declares.
import pathlib, re, shutil
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
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" tools:node="remove" />
"""
if "ACCESS_FINE_LOCATION\" tools:node" not in x:
    x = x.replace("</manifest>", add + "</manifest>")
m.write_text(x)
print("android patched")
print(s[s.find("repositories"):s.find("repositories") + 300])
