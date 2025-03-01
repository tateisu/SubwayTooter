package buildLogic.util

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import java.io.File

internal fun Project.fileOrNull(path: String): File? =
    file(path).takeIf { it.exists() }

internal inline fun <reified T:Task> TaskContainer.withTypeEx(
    noinline block: T.()->Unit,
) = withType(
    T::class.java,
    object :Action<T>{
        override fun execute(t: T) {
            block.invoke(t)
        }
    }
)
