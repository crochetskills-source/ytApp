# 🔔 YouTube Video Bell

An ultra-lightweight Android app that monitors YouTube channels and rings a bell / alarm sound on your phone as soon as a new video is published — with **near-zero internet data usage**.

---

## ⚡ Why Near-Zero Internet Usage?

Unlike the YouTube app or web browsers that download megabytes of thumbnails, JavaScript scripts, and video metadata:
- **Tiny Atom/RSS Feeds**: This app polls YouTube's official XML feed directly (`https://www.youtube.com/feeds/videos.xml?channel_id=...`).
- **~2 to 4 KB per check**: An entire check downloads only raw text.
- **Header Caching**: Uses HTTP headers to avoid downloading if no change has occurred.
- **No Video Streaming**: The app never plays or downloads video content unless you tap "Watch Video".

---

## ✨ Features

- **Multiple Channels**: Add and monitor as many YouTube channels as you want.
- **Smart URL & @Handle Resolution**: Paste `@handle` (e.g. `@mkbhd`), channel links, or channel IDs directly.
- **Customizable Alert Behavior in Settings**:
  - **Bell Sound Options**:
    - 🔔 *Classic Brass Bell* (Synthesized harmonic desk-bell chime — loud & crisp, 100% offline).
    - 🎶 *Gentle Two-Tone Chime* (Ding-dong melody).
    - 🚨 *Urgent Alarm Tone*.
    - 📱 *System Default Ringtone / Alarm*.
  - **Looping Alarm**: Choose between continuous looping alarm (rings until you tap "Stop Bell") or a single chime.
  - **Override Silent Mode**: Break through Do-Not-Disturb / Mute using the Android Alarm audio stream.
  - **Vibration**: Configurable vibration patterns.
  - **Volume Slider**: Dedicated volume control with instant test preview.
  - **Test Bell Button**: Test the sound and volume immediately from Settings.
- **Background Foreground Service**:
  - Uninterrupted monitoring even in Android Doze mode.
  - Interactive notification with "Check Now" and "Stop Bell" quick action buttons.
- **Pull-To-Refresh**: Instantly checks all feeds manually anytime.
- **Alert History**: Logs every new video detected with timestamps and a direct "Watch Video" button.
- **Wi-Fi Only Mode**: Optional toggle to pause checks when roaming or on cellular data.

---

## 🚀 How to Build & Run

### Method 1: Using Android Studio
1. Open Android Studio.
2. Select **Open an Existing Project** and choose the `/Volumes/Data/Test/YTVideoBell` folder.
3. Connect your Android phone via USB (with USB Debugging enabled) or start an emulator.
4. Click **Run 'app'** (Shift + F10).

### Method 2: Command Line Build
```bash
cd /Volumes/Data/Test/YTVideoBell
./gradlew assembleDebug
```
The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

Install on your connected phone:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Method 3: GitHub Actions (Cloud Build)
Push this repository to GitHub. The included `.github/workflows/build.yml` workflow will automatically compile the APK and provide it as a downloadable artifact in the **Actions** tab.
