package buildLogic.util

import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.typeOf
import org.gradle.plugin.use.PluginDependency

internal fun VersionCatalog.version(name: String): String =
    findVersion(name).get().requiredVersion

internal fun VersionCatalog.plugin(name: String): PluginDependency =
    findPlugin(name).get().get()

internal fun VersionCatalog.pluginId(name: String): String =
    findPlugin(name).get().get().pluginId

internal fun VersionCatalog.library(name: String): MinimalExternalModuleDependency =
    findLibrary(name).get().get()

internal fun VersionCatalog.bundle(name: String): Provider<ExternalModuleDependencyBundle?> =
    findBundle(name).get()

internal val Project.libs: VersionCatalog
    get() = extensions.getByType(typeOf<VersionCatalogsExtension>()).named("libs")
