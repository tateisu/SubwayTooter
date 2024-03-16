import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version Vers.kotlinVersion
    `java-library`
}

java {
    sourceCompatibility = Vers.javaSourceCompatibility
    targetCompatibility = Vers.javaTargetCompatibility
}

val compileKotlin: KotlinCompile by tasks
val compileTestKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    jvmTarget = Vers.kotlinJvmTarget
    freeCompilerArgs = listOf(
        "-opt-in=kotlin.ExperimentalStdlibApi",
    )
}
compileTestKotlin.kotlinOptions{
    jvmTarget = Vers.kotlinJvmTarget
    freeCompilerArgs = listOf(
        "-opt-in=kotlin.ExperimentalStdlibApi",
    )
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to arrayOf("*.jar"))))
    testImplementation("junit:junit:${Vers.junitVersion}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Vers.kotlinVersion}")
}
