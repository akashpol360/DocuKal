# DocuKal

Offline-first personal document, warranty and expiry-date manager for Android.

## Stack
Kotlin, Jetpack Compose (Material 3), Room, Coroutines/Flow, MVVM, Navigation
Compose, Android Storage Access Framework, AlarmManager for local reminders.

## Privacy
No account, cloud database, analytics, internet API, API key, Firebase,
Supabase, AI service, payment service, or external REST API is used. All
metadata lives in a local Room database; attachments stay referenced through
Android's Storage Access Framework with a persisted read grant so they remain
openable across app restarts and device reboots.

## Build
Open the project in Android Studio (Giraffe/Koala or newer) and let Gradle
sync, then run on a device or emulator.

Command line:
```
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```
APK output: `app/build/outputs/apk/debug/app-debug.apk`

## What changed from the original build
- Added the Jetpack Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`),
  which Kotlin 2.0+ requires separately from the Kotlin plugin itself. Without
  it the project fails to build anywhere `buildFeatures.compose = true` is set
  - this was the reported build-breaking bug.
- Fixed a race condition that reseeded the ten default categories as
  duplicates on every cold start.
- Picked attachments (documents and warranty invoices) now get a persisted
  read permission, so "Open Attachment" keeps working after the app is killed
  or the device reboots.
- Warranties and important dates now schedule local reminders the same way
  documents do (previously only documents did).
- Tapping a reminder notification now opens the specific document/warranty/
  reminder it was for, instead of just launching the app.
- Fixed the in-app Dark theme not affecting the system status bar.
- Added a proper adaptive launcher icon (foreground + background layers).
- Package renamed `com.mydocuments.app` -> `com.docukal.app`; app renamed
  "My Documents" -> "DocuKal".

## Notes
Android 13+ notification permission is requested at first launch. Backups are
local ZIP files containing application metadata; import adds records without
overwriting existing data, and de-duplicates categories by name on repeat
imports.


## Ads & Premium setup

The app now includes AdMob interstitial ads at these save points only:
- Save Document
- Add Warranty → Save
- Add Important Date → Save

Premium users do not receive those ads. Premium is a Google Play one-time in-app
purchase using product ID `docukal_premium`.

Before release:
1. Create an AdMob app and interstitial ad unit.
2. Replace the test AdMob App ID in `app/src/main/AndroidManifest.xml`.
3. Replace `AD_UNIT_ID` in `app/src/main/java/com/docukal/app/billing/AdManager.kt`.
4. Create the Google Play INAPP product `docukal_premium` and set its price.
5. Upload an internal/closed test build to Google Play and test the purchase with a
   licensed tester account.

The project intentionally uses Google test ad IDs until you replace them, so live
ads are not accidentally served during development.
