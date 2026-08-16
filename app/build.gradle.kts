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
        versionCode = 12
        versionName = "1.2.0"

        val openRouterApiKey = providers.gradleProperty("OPENROUTER_API_KEY")
            .orElse("")
            .get()
        val openRouterModel = providers.gradleProperty("OPENROUTER_MODEL")
            .orElse("google/gemini-3.5-flash-lite")
            .get()

        buildConfigField("String", "OPENROUTER_API_KEY", openRouterApiKey.asBuildConfigString())
        buildConfigField("String", "OPENROUTER_MODEL", openRouterModel.asBuildConfigString())
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

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
