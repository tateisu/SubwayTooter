buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        // room のバージョンの影響で google-services を上げられない場合がある
        classpath(libs.google.services)
        // classpath(libs.unmockplugin)
    }
}

plugins {
    // alias(libs.plugins.unmock) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {


    // configurationのリストを標準出力に出す
    // usage: ./gradlew -q --no-configuration-cache :app:printConfigurations
    tasks.register("printConfigurations") {
        doLast {
            println("project: ${project.name} configurations:")
            for (c in configurations) {
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
