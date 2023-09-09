import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
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
        jvmToolchain( Vers.kotlinJvmToolchain)
    }
    kotlinOptions {
        jvmTarget = Vers.kotlinJvmTarget
    }
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = Vers.kotlinJvmTarget
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":apng"))
    implementation(project(":base"))

    testImplementation("junit:junit:${Vers.junitVersion}")
}
