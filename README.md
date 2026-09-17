# ConsTrakr Android

Native Android port of [ConsTrakr iOS](https://github.com/joven16/constrakr).

**Source of truth:** iOS Swift implementation + [`../ConsTrakr/docs/IOS_ANDROID_PARITY.md`](../ConsTrakr/docs/IOS_ANDROID_PARITY.md)

| Target | Device | Android |
|--------|--------|---------|
| Dev kiosk | Samsung Galaxy A17+ | 13+ |
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
| 7 — Kiosk + maintenance mode | Done (Device Owner, Lock Task, App PIN + admin code) |
| 8 — Device fleet tracking | Done (WorkManager heartbeat, Room queue, admin diagnostics) |
| 9 — Field device tuning | Galaxy A17 (production) · Galaxy S8 (legacy dev) |

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

### Device fleet tracking

Admin-only (Settings → Advanced, after admin code):

- Toggle **Device tracking** and set normal/active heartbeat intervals (minimum 15 minutes).
- Open **Device tracking diagnostics** from the Settings hub for last location/sync, pending queue count, and manual **Collect & sync now**.

Heartbeats are queued locally when offline and uploaded via `POST /constrakr-api/devices/heartbeat` when signed in. Location is one-shot / last-known only (same permissions as site geofence). Disabled by default.

---

## Galaxy A17 — Device Owner + kiosk setup

**Primary production target:** Samsung Galaxy A17 (Android 13+, One UI).

Use a **factory-reset** device or a device with **no Google account / no other device owner**.  
ConsTrakr must be the **first** Device Owner on the device.

**Recommended order:** factory reset → skip accounts during setup → install ConsTrakr → set Device Owner → **then** add a **company Samsung account** for Find My + FRP (see step 8).  
Adding Samsung/Google accounts **after** Device Owner is provisioned is safe and does not remove kiosk mode (verified on Galaxy A17).

### 1. Prepare the phone

1. Factory reset the Galaxy A17 (recommended for kiosk).
2. During initial setup, **skip Google and Samsung account** — many Samsung builds block Device Owner if an account is added too early.
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
2. Enter the **App PIN** (6-digit local PIN; change under **More → Settings → Advanced** with admin code).
3. Lock Task pauses so you can reach Wi‑Fi, updates, or system settings.
4. Tap **Return to kiosk now** in More, or wait for the 15‑minute window to expire — kiosk re-enters automatically.

Do **not** remove Device Owner for routine Wi‑Fi or diagnostics.

### 7. Anti-theft (Device Owner)

When provisioned as Device Owner, ConsTrakr applies:

- **Factory reset blocked** from Settings (`DISALLOW_FACTORY_RESET`)
- **Safe mode blocked** (`DISALLOW_SAFE_BOOT`)
- **Uninstall blocked** for managed apps (`DISALLOW_UNINSTALL_APPS`)

These reduce casual reset/tampering but **do not** stop hardware recovery wipe (Volume + Power). For that layer, enable **Samsung Find My Mobile** with a company Samsung account (Factory Reset Protection), or use **Samsung Knox Guard** for fleet devices.

### 8. Samsung account + Find My (after Device Owner)

Do this **after** steps 3–5 succeed (`adb shell dpm list-owners` shows `com.constrakr`):

1. Enter **maintenance mode** (7× logo tap + App PIN).
2. Open **Settings → Accounts → add Samsung account** (use a **company** account, not a personal one).
3. Enable **Settings → Security and privacy → Find My Mobile**.
4. Optional: add the same account as Google account if you use Google services on the tablet.
5. Return to kiosk (More → Done, or wait for maintenance timeout).

**Factory Reset Protection (FRP):** if the phone is wiped, setup will require that Samsung/Google account before the device can be used again.

Store company account credentials securely (password + 2FA backup). Without them, a wiped device is hard to recover.

---

## Production readiness checklist

Use this before deploying additional site tablets.

| Item | Status / action |
|------|-----------------|
| Device Owner + Lock Task | `adb shell dpm list-owners` → `com.constrakr` |
| Kiosk auto-start on boot | Enabled under Advanced; reboot once to verify |
| App PIN changed from default | **More → Settings → Advanced** (admin code required) |
| Sync account signed in | **More → Sync Account** — one IMS sync user per device or shared site account |
| Admin users assigned on web | **People → Devices → Edit** — assign users with 6-digit admin codes |
| Job sites synced | At least one site with GPS pin on device |
| ML models loaded | Advanced diagnostics: AdaFace + MiniFAS = **loaded** |
| IMS migrations deployed | Server: `migrate constrakr` through `0026` (heartbeat + play sound) |
| Device tracking (optional) | Advanced → enable tracking; verify on **People → Devices** |
| Samsung Find My + FRP | Company Samsung account added **after** Device Owner (step 8) |
| APK type | Prefer **release** build for production (`assembleRelease`); debug OK for pilot |

**Pilot-ready:** your current Galaxy A17 setup is suitable for on-site trial if face models load, sync works, and server migrations are applied.

**Fleet-ready:** also use release APK, company Samsung accounts, documented App PIN/admin codes, and a rollback plan (adb maintenance + APK reinstall).

---

## How long the app stays on the phone

| What | Duration |
|------|----------|
| **ConsTrakr installed (Device Owner)** | Stays until hardware recovery wipe, `adb dpm remove-device-owner`, or Device Owner removal. Survives reboots and APK updates (`adb install -r`). Settings factory reset is blocked while Device Owner is active. |
| **Kiosk / Lock Task** | Stays on while kiosk is enabled in Advanced. Paused only during the 15‑minute maintenance window. |
| **Local data (employees, attendance queue)** | Persists on device until app data is cleared or device is wiped. Works offline. |
| **Sync session (upload to server)** | Stays signed in on device. Minimum **90 days**; each successful sync extends **+30 days** (max **1 year**). After expiry, sign in again under **More** — local attendance queue is kept, uploads resume after re-login. |
| **Debug vs release APK** | Same persistence; release is recommended for production (smaller, not debuggable). |

The app does **not** expire or uninstall itself automatically.

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
