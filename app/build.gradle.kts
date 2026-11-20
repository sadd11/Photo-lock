plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Данные ключа берутся ТОЛЬКО из переменных окружения (GitHub Secrets), в коде их нет
val keystorePath: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.wafuv.photolock"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.wafuv.photolock"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
