# Plan — car-launcher phase 1 (first APK)

Status: approved (owner, 2026-11-06: «ابدأ لين تخلص التطبيق وتجهز لي أول APK»)

## Goal
Ship a light native home launcher that shows design 1 «طلبك» on his TS10S. Every action in it must really work.

## Decisions (agreed in chat)
- **Fully native Kotlin with framework Views only:** no Compose, no WebView, no AppCompat. The owner chose «الأخف».
- **Several designs are selectable;** design 1 «طلبك» is the first. Design is a setting from day one, even with only one design.
- **Screen:** 768×1024 at 160 dpi. With the climate bar shown the app area is 768×832; with it hidden it's 768×976. Text is at least 24px (24dp).

## Scope
1. **Home (design 1)**, top to bottom:
   - top row: time, Hijri + Gregorian date, RAM/CPU pill, all-apps, settings, Home, Back
   - 4 app tiles
   - cards for speed, weather and next prayer
   - a Google Maps panel (tap = real Google Maps)
   - a media panel (prev/play/next for whatever is playing)
2. **Editable bottom 4:** long-press a tile to pick any installed app; the choice is saved.
3. **All-apps drawer** listing installed apps.
4. **«الذاكرة والسرعة» page:**
   - total and free RAM
   - recently used apps with «إيقاف», which uses `killBackgroundProcesses`; allowed on Android 12
5. **Floating Back/Home over any app,** plus an optional tiny RAM/CPU readout. This is an AccessibilityService overlay; back uses `performGlobalAction(BACK)`.
6. **Data sources:**
   - speed from GPS
   - prayer times computed on-device (Umm al-Qura: Fajr 18.5°, Isha = Maghrib + 90 min)
   - weather from Open-Meteo (free, no key)

## Out of scope (phase 2)
- Google Maps live inside the tile (the freeform-window test)
- TopWay car data (the AIDL)
- now-playing titles (needs notification-listener access)
- design 2+

## Done when
- `gradle :app:assembleDebug` succeeds in Docker and produces an APK.
- Installed on the unit (with the owner's OK): it's selectable as Home, apps open, the four tiles can be edited, the memory page shows real numbers, and Back/Home float over Google Maps.
- One-time grants are done over ADB: accessibility service, usage access, location.
