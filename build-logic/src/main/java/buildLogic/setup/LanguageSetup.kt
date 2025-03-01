package buildLogic.setup

import buildLogic.util.libs
import buildLogic.util.version
import buildLogic.util.withExtension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal fun Project.setupKotlin() {
    withExtension<KotlinProjectExtension>("kotlin") {
        jvmToolchain(libs.version("kotlinJvmToolchain").toInt())
    }
    tasks.withType(KotlinJvmCompile::class.java).configureEach {
        with(compilerOptions) {
            jvmTarget.set(JvmTarget.fromTarget(libs.version("kotlinJvmTarget")))
            freeCompilerArgs.set(
                listOf(
                    "-opt-in=kotlin.ExperimentalStdlibApi",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
                    // "-Xopt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                    // "-Xopt-in=androidx.compose.animation.ExperimentalAnimationApi",
                )
            )
        }
    }
}
