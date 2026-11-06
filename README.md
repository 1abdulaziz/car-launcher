# car-launcher

A lightweight native Android home-screen launcher for a vertical "Tesla-style" car head unit
(TS10S, UNISOC UIS7862, Android 12, 768×1024 @160 dpi). It's built with framework Views and the
Kotlin stdlib only (no libraries), so it stays fast on the unit.

The app is **bilingual (Arabic + English)**. It follows the system language, or you can override
it in Settings → Language. It will offer several selectable designs; design 1 «طلبك» ("Talabak")
is the first.

## Features (v0.1.0)
- **Home (design 1):**
  - top row: clock, Gregorian + Hijri (Umm al-Qura) date, RAM/CPU pill, all-apps, settings, Home, Back
  - **4 app tiles:** long-press a tile to change it
  - **cards:** GPS speed, weather (Open-Meteo, no API key) and the next prayer (computed on-device, Umm al-Qura method)
  - **two big app panels:** Google Maps on top and YouTube below by default. **Long-press a panel to put any app in it.** The bottom panel adds media keys (prev / play-pause / next) that control whatever is playing.
- **Settings:**
  - language (System / العربية / English)
  - show or hide every block on the home screen
  - the floating buttons and the mini RAM/CPU readout
  - the memory page
  - the design
- **Memory & speed:** total and free RAM, recently used apps, and one-tap stopping of their background processes (`killBackgroundProcesses`, allowed for other apps on Android 12). Car/system apps (`com.tw.*`, car settings, Google Play services, SystemUI) are never touched.
- **Floating Back / Home over any app:** an accessibility-service overlay (`performGlobalAction`), draggable, with an optional RAM/CPU readout. It hides itself while the launcher is in front.

## Build (inside Docker only, nothing on the host)
```bash
docker compose build android
docker compose run --rm android gradle :app:assembleDebug
```
The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.
Toolchain: AGP 9.5.0 (built-in Kotlin), Gradle 9.6.0, JDK 17, build-tools 36.0.0.

## Install on the unit (wireless ADB)
1. On the unit: Developer options → Wireless debugging → on. (The first time, pair with `adb pair <ip>:<port> <code>`.)
2. On the computer:
   ```bash
   adb mdns services
   adb connect <ip>:<port>
   adb install -r app-debug.apk
   ```
3. One-time grants:
   ```bash
   adb shell pm grant dev.carlauncher android.permission.ACCESS_FINE_LOCATION
   adb shell appops set dev.carlauncher GET_USAGE_STATS allow
   adb shell settings put secure enabled_accessibility_services dev.carlauncher/.FloatService
   adb shell settings put secure accessibility_enabled 1
   ```
   From v0.2.0, also run:
   ```bash
   adb shell cmd notification allow_listener dev.carlauncher/dev.carlauncher.MediaListener   # now-playing on the music card
   adb shell settings put global hidden_api_policy 1                                          # live app window (freeform launch)
   ```
4. Press Home and choose **Car Launcher** as the default home app. The factory launcher stays installed and can be chosen again at any time.

## v0.2.0 — «Factory look» (design 1)
A faithful copy of the head unit's factory (Dofun) home screen, drawn with our own artwork:
- **Top:** six apps over a label strip.
- **Middle:** a **live app window**. The chosen app (Google Maps by default) runs as a freeform window sized to the box. A strip under it holds quick-switch icons (tap to swap the window app, + to add, long-press to remove) and a «Full screen» button.
- **Bottom:** two swipeable pages:
  - music card (now playing from any app's media session) + clock and weather + radio card
  - the car + speed
- «Talabak» stays selectable as design 2 (Settings → Design).

## Roadmap
- **Phase 2:**
  - show the chosen apps **live inside the panels** as freeform windows, the way the factory launcher shows Google Maps
  - car data through the head unit's TopWay car-info service
  - now-playing titles
  - more designs

## Versions
Each version lives on a `release/vX.Y.Z` branch and is merged by the owner; after the merge it gets a `vX.Y.Z` tag.
