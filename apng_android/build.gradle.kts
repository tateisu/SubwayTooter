plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "jp.juggler.apng"
    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        minSdk = Vers.stMinSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
        freeCompilerArgs = listOf(
            // "-opt-in=kotlin.ExperimentalStdlibApi",
            // "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            // "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            // "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            // "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }

    kotlin {
        jvmToolchain(Vers.kotlinJvmToolchain)
    }
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)

    api(project(":apng"))
    implementation(project(":base"))

    implementation(libs.glide)
    implementation(libs.webpDecoder)

    // テストコードはない…
}
