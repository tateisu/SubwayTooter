package buildLogic.util

import org.gradle.api.Project
import java.io.File

internal fun Project.fileOrNull(path: String): File? =
    file(path).takeIf { it.exists() }
