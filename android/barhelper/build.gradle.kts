plugins {
    id("com.android.application")
}

// Tiny companion whose ONLY job is hiding the factory climate bar (System `show_navigationbar`).
// Why a separate APK: on his TS10S the unit resets that key to 1 on every car start, and Android refuses non-public
// System keys from apps targeting API ≥ 23 («You cannot keep your settings in the secure settings», seen 2026-11-06
// from dev.carlauncher even with WRITE_SECURE_SETTINGS). Apps targeting 22 are still allowed, and WRITE_SETTINGS is
// granted to them at install. Keeping it a separate APK leaves the main app on a modern target.
android {
    namespace = "dev.carlauncher.barhelper"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.carlauncher.barhelper"
        minSdk = 22
        @Suppress("ExpiredTargetSdkVersion") // deliberate, see above — Android 12 still installs targetSdk 22 (14+ would not)
        targetSdk = 22
        versionCode = 1
        versionName = "0.4.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { disable += "ExpiredTargetSdkVersion" }
}
