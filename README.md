# Email Notifications for Android

## Android app

The `app/` directory is a native Android app built with Kotlin, Jetpack
Compose, and Material 3. It polls IMAP in a foreground service and shows a
high-priority Android notification for each unread email.

Open the project in Android Studio, build it, install it, and exempt it from
battery optimization. Gmail users should use an App Password and enable IMAP.
Open the project root (the folder containing `settings.gradle`), not a source
folder. Android Studio must complete **Sync Project with Gradle Files** before
the app is opened.

## Background Reliability & Resilience

The app includes three mechanisms to ensure continuous 24/7 background operation:

1. **Auto-restart on Device Boot (`BootReceiver`)**:
   - Listens to `BOOT_COMPLETED` and `QUICKBOOT_POWERON` broadcasts.
   - Automatically restarts `ForwardService` and reenqueues `RestartWorker` upon device restart (if monitoring was enabled).

2. **Battery Optimization Exemption UI**:
   - In-app status indicator and button to request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   - Prevents aggressive Android Doze and OEM power managers from killing the service or restricting network connections.

3. **WorkManager Fallback (`RestartWorker`)**:
   - Enqueues a periodic WorkManager check every 15 minutes (`ExistingPeriodicWorkPolicy.UPDATE`).
   - If `ForwardService` is terminated by the OS due to memory pressure, `RestartWorker` restarts the service or executes an immediate poll fallback.
   - Cleanly cancelled when the user taps **Stop monitoring**.

## Android setup

1. Enable IMAP for the email account. For Gmail, create an **App Password**; do not use your normal account password.
2. Open this folder in Android Studio and build/install the `app` module (or install `app-debug.apk`).
3. Enter the IMAP host, email, and App Password.
4. Tap **Start monitoring**.
5. Tap **Disable Battery Optimization** in the app to exempt it from system power-saving restrictions.

The foreground service checks every 60 seconds. When it finds an unread
message, it posts a high-priority notification containing the sender, subject,
and body. The email is marked read after the notification is posted.

The IMAP password is stored using Android Keystore-backed encrypted
preferences. Do not commit credentials or `local.properties`.
