# Matinee design notes

Reading this as: a TV home screen for one household, used from a sofa with a remote, in a dark cinematic style based on the owner's reference renders in `../design/`. Dial: ENERGY 2 / RHYTHM 1 / MOTION 2.

## Decisions and reasons

- **Dark theme only.** A TV launcher is viewed in dim rooms, and a light full-screen surface is glaring at 55 inches. No light mode.
- **Colour.** Near-black base, white text, one accent per screen taken from the focused app's brand colour. Amber is reserved for actions that change something (free up RAM, notification count). Green marks a live state (receiver ready, toggle on); red marks a problem (RAM above the warning level, no network).
- **Typeface: Inter.** Large x-height and open shapes stay legible at 3 m, the variable weights cover the 400 to 800 range the hierarchy needs, and it has full Turkish glyph coverage.
- **Icons: Tabler outline.** One stroke weight across system icons. Apps appear with their own installed icon and HDMI devices with generic device icons; no third-party brand logos are bundled. Icons are chosen per meaning; the lightning glyph was dropped from "free up RAM".
- **Focal point.** The focused app's name is the one dominant element. When the app has a last-played item, its artwork takes the right side instead of the app logo.
- **Glow and elevation.** Only the focused card glows, in its own accent, because focus must be visible from across the room. Nothing else carries a shadow.
- **Motion.** Focus scale with a spring, crossfades between apps, and a static tilt in edit mode to signal that cards can move. No looping animation. "Reduce motion" in settings turns all of it off.
- **Text size.** Nothing below 9.5 sp on the 960x540 canvas (19 px at 1080p); body text is 11 sp or larger.
- **Status marks.** Every dot or badge marks a real state: receiver availability, unread notifications, network strength. Rows that only show information have no chevron and no action.
