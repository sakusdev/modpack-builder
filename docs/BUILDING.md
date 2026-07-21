# Building PackDroid

## Requirements

- JDK 17
- Android SDK Platform 35
- Gradle 8.9 or newer

## Commands

```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions runs the same test and build commands and uploads the APK as `PackDroid-debug-apk`.
