import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `kotlin-dsl`
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = libs.versions.javaSourceCompatibility.get()
    targetCompatibility = libs.versions.javaTargetCompatibility.get()
}
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get())
}
kotlin {
    jvmToolchain(libs.versions.kotlinJvmToolchain.get().toInt())
}

//
//
//}
//val compileKotlin: KotlinCompile by tasks
//compileKotlin.kotlinOptions.jvmTarget = "1.8" // javaの方とversionをあわせる
//
//kotlinOptions {
//    jvmTarget = Vers.kotlinJvmTarget
//}

dependencies {
    implementation(libs.gradlePlugin.android)
    implementation(libs.gradlePlugin.kotlin)
}

gradlePlugin {
    plugins {
        listOf(
            "buildLogic.StAndroidApp",
            "buildLogic.StAndroidLib",
            "buildLogic.StJvmLib",
        ).forEach {
            // registerの引数nameは使われない
            // id は プラグイン利用側から指定される
            // implementationClass はプラグイン実装クラスのFQCN
            register(it) {
                id = it
                implementationClass = it
            }
        }
    }
}
