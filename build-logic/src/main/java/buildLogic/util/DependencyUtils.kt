package buildLogic.util

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

internal fun DependencyHandler.addEx(
    configurationName: String,
    dependencyNotation: Any,
) = add(configurationName, dependencyNotation)

internal fun DependencyHandler.addEx(
    configurationName: String,
    dependencyNotation: Any,
    block: (Dependency.() -> Unit)? = null,
) = add(configurationName, dependencyNotation)!!.apply { block?.invoke(this) }

internal fun DependencyHandler.implementation(
    notation: Any,
    block: (Dependency.() -> Unit)? = null,
) = addEx("implementation", notation, block)

internal fun DependencyHandler.testImplementation(
    notation: Any,
    block: (Dependency.() -> Unit)? = null,
) = addEx("testImplementation", notation, block)

internal fun DependencyHandler.coreLibraryDesugaring(
    notation: Any,
    block: (Dependency.() -> Unit)? = null,
) = addEx("coreLibraryDesugaring", notation, block)


