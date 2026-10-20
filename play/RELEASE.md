# Releasing Matinee to Google Play

## Signing

The upload key is in `~/.matinee-signing/` (`upload.jks` plus `keystore.properties` with its password). It is outside the project on purpose. Back up that folder somewhere private, such as a password manager. With Play App Signing, a lost upload key can be reset through Play support, but that takes days.

Without that folder, release builds come out unsigned.

## Build

```bash
cd launcher
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`. Bump `versionCode` in `app/build.gradle.kts` for every upload.

## Play Console checklist

1. Create the app: name "Matinee: TV Launcher", app, free.
2. Release > Advanced settings > Form factors: add **Android TV** and opt in. The app declares `android.software.leanback` as required, so phones won't see it.
3. Store listing: text from `listing.md`; at least one unedited TV screenshot (1920x1080); TV banner 1280x720; feature graphic 1024x500; app icon 512x512.
4. Privacy policy: host `privacy-policy.md` at a public URL and fill in the contact email.
5. App content: answers in `data-safety.md`.
6. Testing: personal developer accounts created after 13 November 2023 must run a closed test with at least 12 testers for 14 days before production. Organisation accounts can go straight to production.
7. Upload the AAB to the testing or production track.

## Things reviewers may ask about

- **Notification access:** used only for media session metadata and Play Store update notifications; disclosed in-app next to the setting and in the privacy policy.
- **KILL_BACKGROUND_PROCESSES** ("Free up RAM"): only closes apps from the user's app list, never system services. On Android 14 and later the system limits this call to the app's own processes, so the button frees little there.
