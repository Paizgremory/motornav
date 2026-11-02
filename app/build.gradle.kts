plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.motornav.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.motornav.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 56
        versionName = "3.29"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
