# WiFi Location Project

This repository contains the Android WiFi indoor positioning app, design documents, and project memory snapshots.

## Main directories

- `wifi-positioning-app/`: Android app source code
- `desgin/`: software design and implementation thinking documents
- `memory/`: dated progress snapshots for resumable work

## Build

Use Android Studio bundled JDK in this environment:

```bash
cd wifi-positioning-app
JAVA_HOME=/home/nio/tools/android-studio/jbr GRADLE_USER_HOME=/home/nio/workspace/wifi_location/.gradle ./gradlew assembleDebug
```
