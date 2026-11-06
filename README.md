# car-launcher

A lightweight native Android home-screen launcher for the owner's vertical TS10S head unit (UIS7862, Android 12, 768×1024 @160dpi).
It ships several selectable designs; design 1 is «طلبك», the identity adopted 2026-11-06 (concept + decisions: `workspace/ideas/20261106-car-launcher.md`).

## Build (inside Docker only)
```bash
docker compose build android
docker compose run --rm android gradle :app:assembleDebug
```
The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

## Install on the car (only with the owner's OK)
Wireless ADB from the Mac: `adb pair` / `adb connect` (see the idea doc «His unit, measured»), then `adb install -r app-debug.apk`.

## .env
None yet.

## Staging
Not applicable. It's a single device, installed manually over ADB.
