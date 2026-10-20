# Matinee

A calm, ad-free home screen for Google TV and Android TV, built around your own apps.

- One app row with a large title, or a grid with a favorites shelf (Settings > Home screen > Layout)
- Last played content from apps that share it, kept on the TV
- HDMI inputs with the connected device's name and power state (HDMI-CEC)
- Clock, weather (Open-Meteo), network name and a quick speed test
- Play Store update and new-device notifications behind one bell
- English and Turkish

Get it on Google Play (testing): join [matinee-testers](https://groups.google.com/g/matinee-testers), then open [the test link](https://play.google.com/apps/testing/com.yigitcan.matinee).

## Build

Requires JDK 17+ and the Android SDK (compile SDK 35).

```bash
./gradlew assembleDebug
```

Release builds are signed only when `~/.matinee-signing/keystore.properties` exists; see [play/RELEASE.md](play/RELEASE.md).

Written in Kotlin with Jetpack Compose. Minimum Android 9 (API 28).

Made by [yigitech.dev](https://yigitech.dev) · [x.com/yigitech](https://x.com/yigitech)
