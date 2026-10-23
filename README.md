<p align="center">
  <img src="play/assets/feature-graphic-1024x500.png" alt="Matinee: a calm home screen for your TV" width="820">
</p>

<p align="center">
  A calm, ad-free home screen for Google TV and Android TV, built around your own apps.
</p>

<p align="center">
  <a href="https://play.google.com/apps/testing/com.yigitcan.matinee">Google Play (beta)</a> ·
  <a href="https://github.com/Jarvis322/matinee/releases/latest">Download APK</a> ·
  <a href="https://yigitech.dev/apps/matinee">Website</a> ·
  <a href="https://yigitech.dev/apps/matinee/privacy">Privacy</a>
</p>

---

Stock TV home screens fill most of the screen with promoted shows and ads. Matinee shows only the apps you installed, in the order you choose, and picks up where you left off.

## Two layouts

**Row.** The focused app's name sits large on the left, the screen takes on its colour, and your last played show appears on the right.

![Row layout](play/assets/screenshots/1-home.png)

**Grid** (the default). A favorites shelf with every other app below it. While the shelf has focus, the last played show appears above it.

| | |
|---|---|
| ![Grid layout with last played](play/assets/grid-light-shelf.png) | ![Grid layout, all apps](play/assets/grid-light-apps.png) |

Switch between them in **Settings → Home screen → Layout**.

## Features

- **Your apps, your order.** Hold OK on a card to move it or remove it. Phone-style apps that some TV boxes ship, such as satellite or IPTV players, can be added from the app list.
- **Make it yours.** Hold OK on a card for a menu: move, rename, change its picture, put it in a folder, lock it with a parental PIN, or hide it. Pictures are chosen as files, so Matinee needs no storage permission.
- **Wallpapers.** Eight built-in wallpapers, an optional slowly moving light, or a picture file of your own, with a Liquid Glass look that shows the wallpaper through the shelf and menus.
- **Parental PIN.** Lock apps, and optionally settings and home screen changes, behind a 4-digit PIN.
- **Last played.** Title, artwork and progress from apps that share their media session. Stored only on the TV.
- **Live TV and HDMI inputs.** TVs with a built-in antenna, cable or satellite tuner get a Live TV shortcut that opens the TV's own broadcast. Each HDMI input shows the connected device's name and whether it is on or in standby (HDMI-CEC). Rename inputs and pick an icon.
- **At a glance.** Clock, weather for a city you choose, network name and a quick download speed test.
- **One bell.** Play Store updates and new-device alerts in one place.
- **Stays your home screen.** On TVs whose built-in launcher keeps Home, such as Google TV, an optional accessibility service opens Matinee when you press Home and whenever the TV brings back its own home screen. It sees only the Home key and when the built-in home screen opens, and never reads the screen.
- **Seven languages.** English, Türkçe, Español, Français, Deutsch, 日本語 and 简体中文, switchable inside the app.
- **No account, no ads, no analytics.**

| Edit mode | Profile menu | Settings |
|---|---|---|
| ![Edit mode](play/assets/screenshots/2-edit-mode.png) | ![Profile menu](play/assets/screenshots/3-profile-menu.png) | ![Settings](play/assets/screenshots/4-settings.png) |

## Install

Matinee is in testing on Google Play:

1. Join the [matinee-testers](https://groups.google.com/g/matinee-testers) group with the Google account you use on your TV.
2. Open the [test link](https://play.google.com/apps/testing/com.yigitcan.matinee) and choose **Become a tester**.
3. Install Matinee on your TV from the Play Store.

To sideload instead, download the APK from the [latest release](https://github.com/Jarvis322/matinee/releases/latest). Prefer Google Play if you want the Home button option: Android 13 and later block accessibility permissions for apps installed from a file, so it would not stay on.

Requires Google TV or Android TV 8 or later.

## After installing

Open Matinee once, then go to **Settings** (the profile button at the top right, then **Settings**).

1. **Make Matinee the home screen.** Go to **Home screen → Default home screen** and choose Matinee in the TV's dialog.
   Google TV and some other TVs don't let you change the default home app, so do step 2 as well.
2. **Turn on the Home button.** Go to **Home screen → Home button opens Matinee** and read the explanation. On the TV's accessibility screen that opens, choose **Matinee Home button** and turn it on.
   From then on, pressing Home, waking or restarting the TV, and backing out of an app all land on Matinee.
   This needs the Google Play version on Android 13 and later.
3. **Allow notification access.** Go to **Home screen → Notification access** and turn it on for Matinee. This powers last played, the AirPlay and Chromecast status, and Play Store update alerts.
4. **Optional.** Pick your weather city and profile name under **Home screen**. Switch the layout or card style there too. Change the language under **About → Language**.

If the TV's accessibility list ever shows **Matinee Home button** as off while it is on, turn it off and on again.

## Build

Kotlin and Jetpack Compose. You need JDK 17 or later and the Android SDK (compile SDK 35).

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```


## AirPlay receiver (experimental)

Turn on **Settings → Home screen → AirPlay receiver** to show an iPhone, iPad or Mac screen on the TV. It uses the protocol library from [UxPlay](https://github.com/FDH2/UxPlay) (GPL-3.0) and [libplist](https://github.com/libimobiledevice/libplist) (LGPL-2.1), vendored under `app/src/main/cpp`. OpenSSL is built locally; run `scripts/build-openssl.sh <OpenSSL 3.0 source dir>` once before the first build.

## License

[GPL-3.0](LICENSE) © Yiğit Can Polat · [yigitech.dev](https://yigitech.dev) · [x.com/yigitech](https://x.com/yigitech)
