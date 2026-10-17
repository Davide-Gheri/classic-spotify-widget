plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.classicspotifywidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.classicspotifywidget"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 shrinks Compose from ~23 MB to a few MB and makes it faster. Manifest
            // components and resources used by the widgets are kept automatically.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideload-only app: sign release builds with the debug key so
            // `assembleRelease` produces an installable APK without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Compose is only used by the setup screen; the widgets stay plain RemoteViews.
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Picks the widget background color from the album cover.
    implementation("androidx.palette:palette:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
