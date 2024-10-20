plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "jp.juggler.icon_material_symbols"

    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        minSdk = Vers.stMinSdkVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = Vers.javaSourceCompatibility
        targetCompatibility = Vers.javaTargetCompatibility
    }
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
    }
}

kotlin {
    jvmToolchain(Vers.kotlinJvmToolchain)
}
