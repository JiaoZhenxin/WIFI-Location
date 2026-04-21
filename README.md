# WiFi Location Project

This repository contains the Android WiFi indoor positioning app, project documents, progress snapshots, and AI handbook materials used to support iterative development.

## Quick Navigation

- [项目沉淀索引.md](/home/nio/workspace/wifi_location/项目沉淀索引.md)
- [wifi-positioning-app](/home/nio/workspace/wifi_location/wifi-positioning-app)
- [desgin](/home/nio/workspace/wifi_location/desgin)
- [memory](/home/nio/workspace/wifi_location/memory)
- [AI 工作手册](</home/nio/workspace/wifi_location/AI 工作手册>)

## Main Directories

- `wifi-positioning-app/`: Android app source code and Gradle project
- `desgin/`: software design, development thinking, and commit convention documents
- `memory/`: dated progress snapshots for resumable work
- `AI 工作手册/`: retrospectives, red-line rules, startup templates, and AI operating guidance

## Suggested Reading Order

If you are newly taking over this project, start here:

1. [项目沉淀索引.md](/home/nio/workspace/wifi_location/项目沉淀索引.md)
2. [2026-04-20_v1.md](/home/nio/workspace/wifi_location/memory/2026-04-20_v1.md)
3. [开发思路.md](/home/nio/workspace/wifi_location/desgin/开发思路.md)
4. [软件设计文档.md](/home/nio/workspace/wifi_location/desgin/软件设计文档.md)
5. [AI工作手册索引.md](</home/nio/workspace/wifi_location/AI 工作手册/AI工作手册索引.md>)

## Build

Use Android Studio bundled JDK in this environment:

```bash
cd /home/nio/workspace/wifi_location/wifi-positioning-app
JAVA_HOME=/home/nio/tools/android-studio/jbr GRADLE_USER_HOME=/home/nio/workspace/wifi_location/.gradle ./gradlew assembleDebug
```

Run unit tests:

```bash
cd /home/nio/workspace/wifi_location/wifi-positioning-app
JAVA_HOME=/home/nio/tools/android-studio/jbr GRADLE_USER_HOME=/home/nio/workspace/wifi_location/.gradle ./gradlew testDebugUnitTest
```

## Repository Status

- Remote repository: `https://github.com/JiaoZhenxin/WIFI-Location.git`
- Default branch: `main`

## Related Skills

Custom reusable skills are indexed in:

- [自定义Skills索引.md](/home/nio/.codex/skills/自定义Skills索引.md)
