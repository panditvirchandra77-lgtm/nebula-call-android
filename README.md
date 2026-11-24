# Nebula Call (Android)

A one-screen Android app for a LiveKit video call with an AI agent ("Sonia").
The phone sends mic + front camera, plays the agent's voice, and shows an avatar
that switches between an **idle** loop and a **talk** loop while the agent speaks.

This README is a complete, copy-paste build guide. Follow it top to bottom.
Do not skip steps. Do not "upgrade" versions. Every version below is known to build.

---

## 0. TL;DR (fastest way, no setup)

1. Push this repo to GitHub (it already is).
2. Open the repo on github.com -> **Actions** tab -> **Build APK** -> **Run workflow**.
3. Wait ~6-10 minutes. Open the finished run -> **Artifacts** -> download `NebulaCall-apk`.
4. Unzip it. You get `app-release.apk`. Install it on the phone (section 6).

That's it. GitHub builds it for free on its own servers. Sections 1-5 are only
for building on your own computer.

---

## 1. What you need (exact versions)

| Thing | Version | Why |
|---|---|---|
| JDK | **17** (Temurin) | AGP 8.7 needs Java 17. Java 21 also works, Java 11 does NOT. |
| Gradle | **8.9** (comes via `./gradlew`, don't install separately) | matches AGP 8.7.3 |
| Android SDK platform | `platforms;android-35` | compileSdk 35 |
| Android build-tools | `build-tools;35.0.0` | aapt2, apksigner, zipalign |
| Platform tools | `platform-tools` | `adb` for installing |
| Internet | yes, first build downloads ~1 GB of deps | Maven Central + Google Maven + JitPack |
| RAM | 4 GB free minimum | `org.gradle.jvmargs=-Xmx3g` |

Project values (do not change unless you know why):

```
compileSdk      = 35
buildToolsVersion = 35.0.0
minSdk          = 24      (Android 7.0+)
targetSdk       = 35
versionCode     = 2
versionName     = 0.1.1
abiFilters      = arm64-v8a, armeabi-v7a, x86_64   (one universal APK, no splits)
applicationId   = com.nebula.avatarcall
AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01
livekit-android 2.30.0, media3 1.4.1, okhttp 4.12.0
```

---

## 2. Install the toolchain (Linux x86_64 / Ubuntu / WSL)

Copy-paste exactly:

```bash
# --- JDK 17 ---
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk unzip wget git
java -version          # must print 17.x

# --- Android SDK command-line tools ---
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools
cd /tmp
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O clt.zip
unzip -q clt.zip -d $ANDROID_HOME/cmdline-tools
mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest

export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# --- SDK packages (say yes to licenses) ---
yes | sdkmanager --licenses
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
```

Put the two `export` lines in `~/.bashrc` so they survive a new terminal.

**macOS:** `brew install --cask temurin@17 android-commandlinetools`, then run the
`sdkmanager` lines. **Windows:** install Android Studio, it installs JDK + SDK; then
use `gradlew.bat` instead of `./gradlew`.

---

## 3. Get the code and point it at the SDK

```bash
git clone https://github.com/panditvirchandra77-lgtm/nebula-call-android.git
cd nebula-call-android
echo "sdk.dir=$ANDROID_HOME" > local.properties
chmod +x gradlew
```

`local.properties` is machine-specific and is git-ignored. Every machine makes its own.

---

## 4. Build

```bash
./gradlew --no-daemon assembleRelease
```

- First run: 5-15 minutes (downloads Gradle 8.9 + all libraries). Later runs: 1-3 min.
- Output: `app/build/outputs/apk/release/app-release.apk` (~57 MB, it carries
  WebRTC native libs for 3 CPU types).
- The release build is signed with the **debug key** on purpose (see section 8), so it
  installs directly with no keystore setup.

Want a smaller APK for one phone only? Edit `app/build.gradle.kts` and keep only
`"arm64-v8a"` in `abiFilters` (almost every phone from 2017+). APK drops to ~25 MB.

---

## 5. Verify the APK before sending it to anyone

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
APK=app/build/outputs/apk/release/app-release.apk

$BT/aapt2 dump badging $APK | grep -E "package:|sdkVersion|targetSdkVersion|native-code"
# expect: package: name='com.nebula.avatarcall' versionCode='2' versionName='0.1.1'
#         sdkVersion:'24'  targetSdkVersion:'35'
#         native-code: 'arm64-v8a' 'armeabi-v7a' 'x86_64'

$BT/apksigner verify --verbose $APK      # expect "Verified using v2 scheme: true"
$BT/zipalign -c -P 16 4 $APK && echo "16KB aligned OK"
ls -l $APK                               # ~57 MB. Much smaller = broken download/build
```

If any check fails, do not ship it. Rebuild.

---

## 6. Install on the phone

### Easiest: adb over USB (gives real error messages)

1. Phone: Settings -> About phone -> tap **Build number** 7 times -> Developer options on.
2. Settings -> System -> Developer options -> **USB debugging** ON.
3. Connect USB, accept the "Allow USB debugging" popup on the phone.

```bash
adb devices                                   # phone must show as "device"
adb uninstall com.nebula.avatarcall           # remove any old copy (ignore "not installed")
adb install -r app/build/outputs/apk/release/app-release.apk
```

If it fails, it prints `Failure [INSTALL_FAILED_...]`. Look it up in section 9.
More detail: `adb logcat -d | grep -iE "PackageManager|PackageInstaller|INSTALL_FAILED"`

### Without a computer

1. Send the APK to the phone (Drive, Telegram "file", USB). Do NOT send it as a photo/media,
   chat apps can rename or compress it.
2. Open **Files** app -> Downloads -> check the file is `.apk` and ~57 MB.
3. Tap it. If asked, allow **Install unknown apps** for the Files app.
4. If Play Protect warns, tap **More details -> Install anyway**.

---

## 7. Using the app

On first open the settings screen asks for:

| Field | Example | Notes |
|---|---|---|
| LiveKit URL | `wss://your-host.ts.net` | `wss://` or `ws://` |
| Token URL | `http://your-host:8093/token` | app calls `GET <url>?room=<room>&identity=<identity>` |
| Manual token | (optional) | paste a JWT instead of using a token server |
| Room | `nebula` | |
| Identity | `jeetu-phone` | |
| Avatar idle loop .mp4 URL | (optional) | looped while agent is silent |
| Avatar talk loop .mp4 URL | (optional) | looped while agent speaks |

Allow mic + camera when asked, then tap Call.

### Token server contract (`token_server.py`)

```bash
pip install fastapi uvicorn livekit-api
export LIVEKIT_API_KEY=...  LIVEKIT_API_SECRET=...   # same as your livekit.yaml
# export AGENT_NAME=sonia   # only if your agent uses explicit dispatch
uvicorn token_server:app --host 0.0.0.0 --port 8093
```

The app accepts either a plain-text JWT body, or JSON with any one of these keys:
`token`, `accessToken`, `participantToken`, `participant_token`.
**`jwt` alone is NOT read.** Non-2xx responses show as `Token server <code>: <body>`.

### How the avatar knows the agent is talking

- No data messages, no track-name matching. Any audio track name works.
- The app picks the remote participant with `kind == AGENT` (else the first remote).
- Every 60 ms: talking if `participant.isSpeaking` OR `participant.audioLevel > 0.02`.
- 450 ms hang-over so it doesn't flicker between words.
- If any remote participant publishes a **video** track (e.g. a lip-sync avatar worker),
  the app shows that video instead of the loops. No app change needed.

### Camera

The phone publishes front camera 480x854 @ 24 fps, max 600 kbps, simulcast off,
dynacast on. If the agent doesn't subscribe to video, LiveKit pauses the encode,
saving phone battery/data. Turn agent video input off if you don't need vision.

---

## 8. Signing (read before giving the app to friends)

- The release build uses the debug keystore (`~/.android/debug.keystore`), which is
  created automatically per machine.
- **Different machine = different key.** An APK from machine B cannot update an APK from
  machine A: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Fix: uninstall the old app first.
- For a stable key, create one once and commit nothing secret:

```bash
keytool -genkeypair -v -keystore nebula.jks -alias nebula -keyalg RSA -keysize 2048 -validity 10000
```

Then add a `signingConfigs { create("release") { ... } }` block reading the passwords
from environment variables, and use it in `buildTypes.release`. Keep `nebula.jks` out of git.

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unsupported class file major version` / `requires Java 17` | wrong JDK | `java -version` must be 17; set `JAVA_HOME` |
| `SDK location not found` | no `local.properties` | `echo "sdk.dir=$ANDROID_HOME" > local.properties` |
| `Failed to find target android-35` / build-tools | SDK packages missing | `sdkmanager "platforms;android-35" "build-tools;35.0.0"` |
| `Could not resolve io.livekit...` | no internet / proxy | check network; JitPack + Maven Central must be reachable |
| Gradle killed / `OutOfMemoryError` | low RAM | close apps, or lower `-Xmx3g` to `-Xmx2g` in `gradle.properties` |
| aapt2 `Syntax error: "(" unexpected` or exec format error | ARM64 Linux host (e.g. Raspberry Pi, Graviton, Termux) | Google ships x86_64 aapt2 only. Get an arm64 aapt2 build and add `android.aapt2FromMavenOverride=/path/to/arm64/aapt2` to `gradle.properties`. Also use an **aarch64** JDK 17. Easiest: just use GitHub Actions (section 0). |
| Phone: "You can't install this app on your device" | file corrupted/renamed in transfer, or old version with another key | check size ~57 MB and `.apk`; uninstall old app; use `adb install` to see the real code |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | signed with a different key | `adb uninstall com.nebula.avatarcall` then install |
| `INSTALL_FAILED_OLDER_SDK` | phone below Android 7.0 | not supported |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | CPU type not in `abiFilters` | keep all 3 ABIs |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | phone has a higher versionCode | uninstall first, or bump `versionCode` |
| `INSTALL_PARSE_FAILED_NOT_APK` / `NO_CERTIFICATES` | incomplete download or unsigned | re-download; run section 5 checks |
| Blocked by Play Protect | unknown developer | More details -> Install anyway |
| App stuck on "waiting for agent" | agent not dispatched to the room | check agent worker logs; set `AGENT_NAME` if explicit dispatch |
| `Token server 4xx/5xx` | token server error | read the body shown; check LIVEKIT keys |

---

## 9b. Web version (no APK needed)

`token_server.py` also serves `web/index.html` at `/`: a browser voice call with Sonia
(LiveKit JS, same speaking detection as the app). Set `LIVEKIT_URL` so the token response
carries the server URL. Open `https://<host>:8093/` in Chrome. To run it inside the Nebula app,
see `NEBULA_APP.md`.

## 10. File map

```
app/build.gradle.kts            versions, SDK levels, ABIs, dependencies
app/src/main/AndroidManifest.xml permissions (camera, mic, internet), cleartext allowed
app/src/main/java/com/nebula/avatarcall/
  MainActivity.kt               settings screen + call screen (Compose)
  CallController.kt             LiveKit connect, token fetch, speaking detection, mic/cam
  AvatarView.kt                 idle/talk loop player (media3), fallback image
  LkVideo.kt                    renders a LiveKit VideoTrack
app/src/main/res/xml/network_security_config.xml   allows http:// token URLs
token_server.py                 FastAPI token endpoint + serves the web call page
web/index.html, web/avatar.jpg  browser voice call page
NEBULA_APP.md                   run the web call as a Nebula App
.github/workflows/build-apk.yml free cloud build, APK as artifact
```

## 11. Making a new version

1. Edit code.
2. In `app/build.gradle.kts` bump `versionCode` (+1) and `versionName`.
3. `./gradlew --no-daemon assembleRelease` (or push and let Actions build).
4. Run section 5 checks. Install with section 6.
