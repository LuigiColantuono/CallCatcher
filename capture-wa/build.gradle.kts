plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.callcatcher.capture.wa"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)

    // Xposed API is provided by the framework at runtime — never bundle it.
    compileOnly(libs.xposed.api)

    // Fallback lookup of WhatsApp's call callback class. Used only inside WaClassFinder.
    implementation(libs.dexkit)
}
