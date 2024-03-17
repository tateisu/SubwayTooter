import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
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
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = Vers.kotlinJvmTarget
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Vers.desugarLibVersion}")

    // dismissSafe, systemService, View.gone() など
    implementation(project(":base"))

    implementation("androidx.core:core-ktx:${Vers.androidxCore}")
    implementation("androidx.appcompat:appcompat:${Vers.androidxAppcompat}")
    implementation("androidx.annotation:annotation:${Vers.androidxAnnotation}")
    implementation("com.google.android.flexbox:flexbox:${Vers.googleFlexbox}")
}
