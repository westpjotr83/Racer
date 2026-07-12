plugins {
    id("com.android.application")
}

android {
    namespace = "nl.groenstad.groenopdebalans"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.groenstad.groenopdebalans.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
