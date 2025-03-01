package buildLogic

import buildLogic.setup.setupKotlin
import buildLogic.util.androidAppExt
import buildLogic.util.androidExt
import buildLogic.util.androidLibExt
import buildLogic.util.fileOrNull
import buildLogic.util.library
import buildLogic.util.libs
import buildLogic.util.pluginId
import buildLogic.util.version
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

class StAndroidApp : Plugin<Project> {
    override fun apply(target: Project) = target.setupAndroidAppOrLib(
        agpCatalogName = "android-application",
    )
}

class StAndroidLib : Plugin<Project> {
    override fun apply(target: Project) = target.setupAndroidAppOrLib(
        agpCatalogName = "android-library",
    )
}

private fun Project.setupAndroidAppOrLib(agpCatalogName: String) {
    with(pluginManager) {
        apply(libs.pluginId(agpCatalogName))
        apply(libs.pluginId("kotlin-android"))
    }
    setupKotlin()

    val stTargetSdk = libs.version("stTargetSdkVersion").toInt()
    androidExt {
        compileSdk = libs.version("stCompileSdkVersion").toInt()
        buildToolsVersion = libs.version("stBuildToolsVersion")

        defaultConfig {
            minSdk = libs.version("stMinSdkVersion").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            vectorDrawables.useSupportLibrary = true
        }
        buildTypes {
            named("release") {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.toVersion(libs.version("javaSourceCompatibility"))
            targetCompatibility = JavaVersion.toVersion(libs.version("javaTargetCompatibility"))
            isCoreLibraryDesugaringEnabled = true
        }
        buildFeatures {
            viewBinding = true
        }
        lint {
            targetSdk = stTargetSdk
        }
        packaging {
            jniLibs.excludes.add("META-INF/LICENSE*")
            resources.excludes.add("META-INF/LICENSE*")
        }
    }
    androidAppExt {
        defaultConfig {
            targetSdk = stTargetSdk
        }
        buildTypes {
            named("release") {
                isMinifyEnabled = false
                proguardFile(getDefaultProguardFile("proguard-android.txt"))
                fileOrNull("proguard-rules.pro")?.let { proguardFile(it) }
            }
        }
    }

    androidLibExt {
        defaultConfig {
            fileOrNull("consumer-rules.pro")?.let { consumerProguardFile(it) }
        }
    }
    with(dependencies) {
        add("coreLibraryDesugaring", libs.library("desugar-jdk"))
    }
}
