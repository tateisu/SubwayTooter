package buildLogic

import buildLogic.setup.setupKotlin
import buildLogic.util.library
import buildLogic.util.libs
import buildLogic.util.pluginId
import buildLogic.util.testImplementation
import buildLogic.util.version
import buildLogic.util.withExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension

class StJvmLib : Plugin<Project> {
    override fun apply(target: Project) = target.setupJvmLib()
}

private fun Project.setupJvmLib() {
    with(pluginManager) {
        apply("java-library")
        apply(libs.pluginId("kotlin-jvm"))
    }

    //
    withExtension<JavaPluginExtension>("java") {
        sourceCompatibility = JavaVersion.toVersion(libs.version("javaSourceCompatibility"))
        targetCompatibility = JavaVersion.toVersion(libs.version("javaTargetCompatibility"))
    }

    //
    setupKotlin()

    //
    with(dependencies) {
        // implementation(fileTree(mapOf("dir" to "libs", "include" to arrayOf("*.jar"))))
        testImplementation(libs.library("kotlin-test"))
    }
}
