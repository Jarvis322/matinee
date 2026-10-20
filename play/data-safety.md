# Play Console answers

## Data safety

- Does your app collect or share any of the required user data types? **Yes**, only because of weather:
  - **Location > Approximate location:** collected and shared with Open-Meteo, for app functionality, not required (the weather widget can be turned off), processed ephemerally. The value is the city the user picked, not device location; declaring it is the conservative reading of Google's definition.
- The Wi-Fi network name (read with location permission) and speed-test results stay on the device and are not collected. The speed test only downloads from Cloudflare; no user data is sent.
- Everything else Matinee reads (installed apps, media playback info, Play Store notifications, HDMI device names) stays on the device, so it is not "collected" under Play's definition.
- Is all user data encrypted in transit? **Yes** (HTTPS to Open-Meteo).
- Can users request deletion? Nothing is stored off the device; uninstalling removes everything.

## Location permission

ACCESS_FINE_LOCATION is requested only when the user turns on "Show Wi-Fi network name", because Android exposes the SSID only to apps with location permission. Device location is never read. Explain this in the Play Console permission questions if asked.

## Accessibility service (Play Console declaration)

`HomeKeyService` is not an accessibility tool (`isAccessibilityTool="false"`). Its only purpose: redirect the remote's Home key to Matinee on TVs whose stock launcher keeps Home through a higher intent-filter priority. It sets only `flagRequestFilterKeyEvents`, handles only KEYCODE_HOME, never requests window content, and transmits nothing. An in-app disclosure with explicit consent ("Agree and continue", focus starts on Cancel) is shown before the system accessibility settings open. Fill the Play Console accessibility declaration with this text and attach a short video of the disclosure and the key working.

## Content rating

Utility app; no user-generated content, no violence, no purchases, no location sharing between users. Expect "Everyone" / PEGI 3.

## App access

All functionality is available without sign-in. Note for reviewers: "Last played, receiver status and Play Store updates need notification access: Settings > Home screen > Notification access."

## Ads

No ads.

## Target audience

18+ is simplest; the app is not designed for children.
