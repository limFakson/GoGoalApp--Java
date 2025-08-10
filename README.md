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
-app/build/outputs/apk/release/app-release-unsigned.apk
```

## 📜 License
### This project is proprietary to GoGoalTV unless otherwise stated.

---

Do you want me to **also include a “Quick Deploy” section** so your site visitors can directly download and install the APK? That would make it end-user friendly instead of just developer focused.

---