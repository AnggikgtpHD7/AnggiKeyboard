plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    namespace = "com.anggik.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anggik.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "2.1.0"
        resourceConfigurations += listOf("en", "in")
    }

    signingConfigs {
        getByName("debug") {
            val fixedKeystore = file("debug.keystore")
            if (fixedKeystore.exists()) {
                storeFile = fixedKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            vcsInfo.include = false
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "DebugProbesKt.bin"
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
    // Pure Android SDK (API 26-36) + Kotlin stdlib: zero external dependency bloat
}
