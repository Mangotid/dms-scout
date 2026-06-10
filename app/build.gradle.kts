plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ua.universalna.dmsscout"
    compileSdk = 34

    defaultConfig {
        applicationId = "ua.universalna.dmsscout"
        minSdk = 29
        targetSdk = 34
        versionCode = 1.1
        versionName = "1.1"
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
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    // Добавляем библиотеку Jsoup для нативного скрапинга сайтов
    implementation("org.jsoup:jsoup:1.17.2")
}
