import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "jp.juggler.anko"

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
    kotlin {
        jvmToolchain(Vers.kotlinJvmToolchain)
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

dependencies {
    implementation("androidx.appcompat:appcompat:${Vers.androidxAppcompat}")
    implementation("androidx.core:core-ktx:${Vers.androidxCore}")
    implementation("androidx.preference:preference-ktx:${Vers.androidxPreferenceKtx}")
    implementation("com.google.android.material:material:${Vers.googleMaterial}")

    testImplementation(kotlin("test"))

    androidTestRuntimeOnly("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core:${Vers.androidxTestCore}")
    androidTestImplementation("androidx.test.ext:junit:${Vers.androidxTestExtJunit}")
}
