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
    testImplementation("junit:junit:${Vers.junitVersion}")
    androidTestImplementation("androidx.test.ext:junit:${Vers.androidxTestExtJunitVersion}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Vers.androidxTestEspressoCoreVersion}")

    implementation("androidx.appcompat:appcompat:${Vers.appcompatVersion}")
    implementation("androidx.core:core-ktx:${Vers.coreKtxVersion}")
    implementation("androidx.preference:preference-ktx:${Vers.preferenceKtxVersion}")
    implementation("com.google.android.material:material:${Vers.materialVersion}")
}
