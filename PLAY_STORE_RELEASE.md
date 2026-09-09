# DocuKal Play Store Release Package

Developer Mode and all Free/Premium simulation controls have been removed from this release source.

## Premium
- One-time Google Play product ID: `docukal_premium`
- Premium users do not receive interstitial ads.
- Premium Insights are available to Premium users.

## Before publishing
1. In `app/src/main/AndroidManifest.xml`, replace the Google test AdMob App ID with your production AdMob App ID.
2. In `app/src/main/java/com/docukal/app/billing/AdManager.kt`, replace the Google test interstitial unit ID with your production AdMob interstitial unit ID.
3. Create the `docukal_premium` one-time INAPP product in Google Play Console and configure its price.
4. Create/use your upload keystore and configure the release signing configuration in Android Studio.
5. Build a signed Android App Bundle (`.aab`) using the `release` variant and upload it to a Play Console testing track before production.

The source package intentionally does not contain a private signing key or passwords.
