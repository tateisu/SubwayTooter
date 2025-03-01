package buildLogic.util

import com.android.build.api.dsl.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import  org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal inline fun <reified T> Project.withExtension(name:String,noinline block:T.()->Unit){
    (this as ExtensionAware).extensions.configure(
        name,
        object: Action<T>{
            override fun execute(t: T & Any) {
                block.invoke(t as T)
            }
        }
    )
}


typealias AndroidExtensionBase = CommonExtension<
        BuildFeatures,
        BuildType,
        DefaultConfig,
        ProductFlavor,
        AndroidResources,
        Installation,
>

// Android Applocation なら真
internal fun Project.isAndroidApplication(): Boolean =
    extensions.findByType(AppExtension::class.java) != null

// この Project が Library Project であるか判定
internal fun Project.isAndroidLibrary(): Boolean =
    extensions.findByType(LibraryExtension::class.java) != null

// android{...} と同じ
// Android Application と Android Library に共通する事にアクセスできる
internal fun Project.androidExt(block: AndroidExtensionBase.() -> Unit) =
    withExtension("android",block)

// android{...} と同じ
// ただしAndroid Application でないなら何もしない
internal fun Project.androidAppExt(block: BaseAppModuleExtension.() -> Unit) {
    extensions.findByType(BaseAppModuleExtension::class.java) ?: return
    withExtension("android",block)
}

// android{...} と同じ
// ただしAndroid Library でないなら何もしない
internal fun Project.androidLibExt(block: LibraryExtension.() -> Unit) {
    extensions.findByType(LibraryExtension::class.java) ?: return
    withExtension("android",block)
}


