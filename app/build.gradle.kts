plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.stilooverboost.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.stilooverboost.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 4
        versionName = "0.4.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
