buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:${Vers.androidGradlePruginVersion}")

        // room のバージョンの影響で google-services を上げられない場合がある
        classpath("com.google.gms:google-services:4.4.1")

        //noinspection DifferentKotlinGradleVersion
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Vers.kotlinVersion}")
        classpath("org.jetbrains.kotlin:kotlin-serialization:${Vers.kotlinVersion}")

        classpath("com.github.bjoernq:unmockplugin:0.7.6")

        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${Vers.detektVersion}")
    }
}

plugins {
    kotlin("jvm") version (Vers.kotlinVersion) apply false
    kotlin("plugin.serialization") version (Vers.kotlinxSerializationPluginVersion) apply true // !!
    id("org.jetbrains.kotlin.android") version (Vers.kotlinVersion) apply false
    id("com.google.devtools.ksp") version (Vers.kspVersion) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()

        // alexzhirkevich/custom-qr-generator
        maven(url = "https://jitpack.io")
    }

    // configurationのリストを標準出力に出す
    // usage: ./gradlew -q --no-configuration-cache :app:printConfigurations
    tasks.register("printConfigurations") {
        doLast {
            println("project: ${project.name} configurations:")
            for( c in configurations){
                println("configuration: ${c.name}")
            }
        }
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
