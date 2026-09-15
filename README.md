# ConsTrakr Android

Native Android port of [ConsTrakr iOS](https://github.com/joven16/constrakr).

**Source of truth:** iOS Swift implementation + [`../ConsTrakr/docs/IOS_ANDROID_PARITY.md`](../ConsTrakr/docs/IOS_ANDROID_PARITY.md)

| Target | Device | Android |
|--------|--------|---------|
| Dev kiosk | Samsung Galaxy S8 | 9 (API 28) |
| Production | Samsung Galaxy A17+ | 13+ |

- **minSdk:** 28  
- **Package:** `com.constrakr`  
- **Stack:** Kotlin · Jetpack Compose · CameraX · ML Kit · TFLite · Room · WorkManager · Device Owner kiosk

---

## Project status

| Phase | Status |
|-------|--------|
| 1 — iOS analysis + parity doc | Done |
| 2 — Android foundation (this repo) | Done |
| 3 — AdaFace TFLite + preprocessing | Code ready · **models not in repo** (export manually) |
| 4 — MiniFASNet + liveness | Code ready · **models not in repo** |
| 5 — Enrollment + attendance UI | Done (Compose tabs, Scanner, Enrollment) |
| 6 — Room + API + sync | Done (roster pull, push employees/embeddings/attendance) |
| 7 — Kiosk + maintenance mode | Done (Device Owner, Lock Task, 7× PIN maintenance) |
| 8 — Field device tuning | Galaxy A17 (production) · Galaxy S8 (legacy dev) |

---

## ML models

Export from iOS Core ML packages and place in:

```text
app/src/main/assets/models/adaface_ir18.tflite
app/src/main/assets/models/minifasnetv2.tflite
```

See `app/src/main/assets/models/README.md`. **Do not commit fake weights.**

---

## Build

Requirements: Android Studio · **JDK 17 for Gradle** · Android SDK 35

Android Studio’s bundled JBR may be Java 21+ — set **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** to **17** (download via Studio if needed).

```bash
cd ConsTrakr-Android
./gradlew :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Install on device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Galaxy A17 — Device Owner + kiosk setup

**Primary production target:** Samsung Galaxy A17 (Android 13+, One UI).

Use a **factory-reset** device or a device with **no Google account / no other device owner**.  
ConsTrakr must be the **first** Device Owner on the device.

### 1. Prepare the phone

1. Factory reset the Galaxy A17 (recommended for kiosk).
2. During setup, **skip Google account** — add a Google account later blocks Device Owner on many Samsung builds.
3. Enable **Developer options** → **USB debugging**.
4. Connect via USB; verify: `adb devices`

### 2. Install debug APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `adb install` hangs on Samsung, use push + pm install:

```bash
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/constrakr.apk
adb shell pm install -r -t /data/local/tmp/constrakr.apk
```

### 3. Set Device Owner (exact command for this project)

```bash
adb shell dpm set-device-owner com.constrakr/.kiosk.ConsTrakrDeviceAdminReceiver
```

Expected success output:

```text
Success: Device owner set to package com.constrakr
```

If it fails:

- Another profile owner / device owner exists → factory reset.
- Google account on device → remove account or factory reset.
- App not installed → install APK first.

### 4. Verify Device Owner

```bash
adb shell dpm list-owners
```

Should list `com.constrakr`.

### 5. Launch ConsTrakr and enter Lock Task Mode

Open the app on device. After Device Owner provisioning, ConsTrakr automatically:

- Sets itself as the default HOME launcher
- Disables status bar and keyguard
- Enters **Lock Task Mode** on launch and resume (not screen pinning)
- Relaunches after reboot (`KioskBootReceiver`)

Kiosk can be toggled under **More → Settings → Advanced → Kiosk mode**.

### 6. Exit kiosk (maintenance)

**Hidden maintenance exit** (15 minutes):

1. Tap **ConsTrakr** in the top bar **7 times** (or tap the **site chip** / **Ready** label on the Scanner tab 7 times).
2. Enter the **maintenance PIN** (factory default: `882741`).
3. Lock Task pauses so you can reach Wi‑Fi, updates, or system settings.
4. Tap **Return to kiosk now** in More, or wait for the 15‑minute window to expire — kiosk re-enters automatically.

Change the maintenance PIN under **More → Settings → Advanced → Maintenance PIN**.

Do **not** remove Device Owner for routine Wi‑Fi or diagnostics.

---

## Architecture (mirrors iOS)

```text
com.constrakr
├── camera/          CameraXManager
├── face/            FaceDetectionService, FacePreprocessor, HeadPoseEstimator
├── recognition/     AdaFaceRecognizer, FaceMatchingService
├── liveness/        MiniFasLivenessDetector, depth/DepthProvider
├── attendance/      AttendanceVerificationEngine
├── enrollment/      (Phase 5)
├── database/          Room (Phase 6)
├── network/           Retrofit DTOs (Phase 6)
├── security/          SecureFaceTemplateStore
├── sync/              WorkManager (Phase 6)
├── kiosk/             KioskController, ConsTrakrDeviceAdminReceiver
└── ui/                Jetpack Compose
```

---

## iOS parity

All thresholds and business rules are documented in [`IOS_ANDROID_PARITY.md`](../ConsTrakr/docs/IOS_ANDROID_PARITY.md).  
Do not change match thresholds without cross-platform calibration.

**TrueDepth:** Not available on Galaxy S8/A17 — `NoDepthProvider` replaces `AVDepthData`; liveness uses MiniFASNet + adaptive active challenges instead.
