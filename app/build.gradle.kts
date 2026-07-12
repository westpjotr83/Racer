plugins {
    id("com.android.application")
}

android {
    namespace = "nl.groenstad.groenopdebalans"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.groenstad.groenopdebalans"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
