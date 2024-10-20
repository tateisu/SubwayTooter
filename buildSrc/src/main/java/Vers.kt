import org.gradle.api.JavaVersion

@Suppress("ConstPropertyName")
object Vers {

    const val stBuildToolsVersion = "34.0.0"
    const val stCompileSdkVersion = 34
    const val stTargetSdkVersion = 34
    const val stMinSdkVersion = 26

    val javaSourceCompatibility = JavaVersion.VERSION_19
    val javaTargetCompatibility = JavaVersion.VERSION_19

    // Compose Compiler 1.5.10 は kotlin 1.9.22 を要求する
    const val kotlinVersion = "2.0.21"


    // Compose Compiler 1.5.10 は jvmTarget = "19" を要求する
    // しかし Android Studio 自体は17で動いてるので単体テスト時に問題がでる
    const val kotlinJvmTarget = "19"

    // Android Studio同梱のJavaツールチェインのバージョン
    const val kotlinJvmToolchain = 21
}
