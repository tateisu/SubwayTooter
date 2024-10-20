plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "jp.juggler.emoji"

    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        minSdk = Vers.stMinSdkVersion
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = Vers.javaSourceCompatibility
        targetCompatibility = Vers.javaTargetCompatibility
    }

    kotlin {
        jvmToolchain(Vers.kotlinJvmToolchain)
    }
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
    }
}
