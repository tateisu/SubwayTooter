import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = Vers.javaSourceCompatibility
    targetCompatibility = Vers.javaTargetCompatibility
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Vers.kotlinJvmTarget))
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.ExperimentalStdlibApi",
        )
    }
}

dependencies {
    // implementation(fileTree(mapOf("dir" to "libs", "include" to arrayOf("*.jar"))))
    testImplementation(libs.kotlin.test)
}
