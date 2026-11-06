plugins {
    id("com.android.application")
}

android {
    namespace = "dev.carlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.carlauncher"
        // His unit is Android 12 (SP1A.210812.016) — API 31.
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
    }

    buildTypes {
        release { isMinifyEnabled = true }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// No dependencies on purpose: framework Views + Kotlin stdlib only («الأخف»).
