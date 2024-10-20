plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "jp.juggler.apng.sample"

    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        targetSdk = Vers.stTargetSdkVersion
        minSdk = Vers.stMinSdkVersion

        versionCode = 1
        versionName = "1.0"

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
    packaging {
        resources {
            pickFirsts += listOf("META-INF/atomicfu.kotlin_module")
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
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
//                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
//                "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi",
//                "-Xopt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
    kotlin {
        jvmToolchain(Vers.kotlinJvmToolchain)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)
    implementation(project(":base"))
    implementation(project(":apng_android"))
    implementation(libs.androidx.appcompat)

    // ないとなぜかIDE上にエラーが出る
    implementation(libs.androidx.activity.ktx)
}
