plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI builds get an increasing versionCode so each APK can update the previous one in place.
val ciRunNumber: Int? = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

// Stable signing key (not committed; CI restores it from the DEV_KEYSTORE_B64 secret). Without it
// the build falls back to a throwaway debug key, and such an APK can't update an installed one.
val devKeystore = file("dev.keystore")

android {
    namespace = "com.callcatcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callcatcher"
        minSdk = 29
        targetSdk = 35
        versionCode = ciRunNumber ?: 1
        versionName = "0.2.0" + (ciRunNumber?.let { "-ci$it" } ?: "-dev")
    }

    signingConfigs {
        create("dev") {
            storeFile = devKeystore
            storePassword = System.getenv("DEV_KEYSTORE_PASSWORD") ?: "callcatcher"
            keyAlias = "callcatcher"
            keyPassword = System.getenv("DEV_KEYSTORE_PASSWORD") ?: "callcatcher"
        }
    }

    buildTypes {
        debug {
            if (devKeystore.exists()) signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (devKeystore.exists()) signingConfig = signingConfigs.getByName("dev")
        }
    }

    packaging {
        // Uncompressed, page-aligned native libs (DexKit): required for an app in /system/priv-app,
        // which the system never extracts libraries for.
        jniLibs.useLegacyPackaging = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":player"))
    implementation(project(":capture-phone"))
    // The app APK is itself the LSPosed module (WAEnhancer model): the xposed_init asset and
    // xposedmodule manifest meta-data from :capture-wa merge in, and its hook classes ride along.
    // The Xposed API stays compileOnly in :capture-wa, so nothing Xposed is bundled; those classes
    // only ever load inside WhatsApp and system_server, where LSPosed provides the API.
    implementation(project(":capture-wa"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
}
