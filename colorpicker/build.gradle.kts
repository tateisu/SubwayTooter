plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.jrummyapps.android.colorpicker"

    resourcePrefix = "cpv_"

    compileSdk = Vers.stCompileSdkVersion
    buildToolsVersion = Vers.stBuildToolsVersion

    defaultConfig {
        minSdk = Vers.stMinSdkVersion

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    lint {
        abortOnError = false
    }
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = Vers.javaSourceCompatibility
        targetCompatibility = Vers.javaTargetCompatibility
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(Vers.kotlinJvmToolchain)
    }
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            //"-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            //"-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            //"-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
        )
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)

    // dismissSafe, systemService, View.gone() など
    implementation(project(":base"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.google.flexbox)
}
