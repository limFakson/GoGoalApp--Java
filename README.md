# 📱 GoGoalTV Android App

GoGoalTV is an Android application that maintains a persistent WebSocket connection to stream and exchange live data.  
It is built with Java and designed to run as a background service so it can stay connected even when minimized.

---

## 🚀 Features
- **Persistent WebSocket connection** — communicates with the server in real-time.
- **Background service** — keeps running even if the app is minimized.
- **Battery optimization exemption** — requests permission to avoid OS killing the service.
- **Custom branding** — "GoGoalTV" splash overlay with `#C62828` title on white background.
- **Optimized for long-running tasks** — uses foreground service notifications.
- **Live Match Fixtures** – Fetches and displays upcoming, live, and finished matches.
- **Pull-to-Refresh** – Swipe down to reload fixtures instantly.
- **Auto Timezone Detection** – Match times automatically display in the user’s local time.
- **Score Centering** – Scores are neatly aligned in the middle.
- **Auto Update Check** – On app open, checks if a new version is available and prompts for download.
- **Offline Image Fallback** – Uses Picasso to load match/team images, with a placeholder if unavailable.
- **Custom UI Colors** – Navigation and status bars styled for a consistent look.

---

## 📂 Project Structure
app/
src/main/java/com/example/nodeapp/ # Java source code
src/main/res/ # Resources (layouts, drawables, values)
build.gradle # Gradle configuration

---

## 🛠 Building the App

### 1. Assemble the Release APK
```bash
./gradlew assembleRelease
```
### This will generate:

```arduino
-app/build/outputs/apk/release/app-release.apk
```
---

## ⚡ Auto Update Logic
### The app compares the installed version with the latest available from the server.
### Uses semantic versioning (1.2.0 < 1.2.1) to ensure only newer versions trigger an update prompt.

---

## 🌍 Timezone Handling
### Match times from API (UTC ISO 8601) are automatically converted to the device’s local timezone using ZonedDateTime (API ≥ 24).

---

## 📷 Image Loading
- **Primary**: Loads images via Picasso from remote URLs.
- **Fallback**: Displays placeholder if image fails to load.
---

## 📜 License
### This project is proprietary to GoGoalTV unless otherwise stated.

---