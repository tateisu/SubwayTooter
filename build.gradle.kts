buildscript {

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${Vers.androidGradlePruginVersion}")

        // room のバージョンの影響で google-services を上げられない場合がある
        classpath("com.google.gms:google-services:4.3.15")

        //noinspection DifferentKotlinGradleVersion
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Vers.kotlinVersion}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Vers.kotlinVersion}")

        classpath("com.github.bjoernq:unmockplugin:0.7.6")

        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${Vers.detektVersion}")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version (Vers.kotlinVersion) apply false
    id("org.jetbrains.kotlin.android") version (Vers.kotlinVersion) apply false
    id("com.google.devtools.ksp") version (Vers.kspVersion) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()

        // com.github.androidmads:QRGenerator
        maven(url = "https://jitpack.io")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(
        arrayOf(
            "-Xlint:unchecked",
            "-Xlint:deprecation",
            "-Xlint:divzero",
        )
    )
}
