plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.voicetranslateime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.voicetranslateime"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        val backendUrl = providers.gradleProperty("IME_BACKEND_BASE_URL")
            .orElse("http://10.0.2.2:8787/")
            .get()
        val sharedToken = providers.gradleProperty("IME_SHARED_TOKEN")
            .orElse("dev-ime-token-change-me")
            .get()

        buildConfigField("String", "IME_BACKEND_BASE_URL", "\"$backendUrl\"")
        buildConfigField("String", "IME_SHARED_TOKEN", "\"$sharedToken\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
