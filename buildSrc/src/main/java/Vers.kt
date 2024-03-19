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
    @Suppress("MemberVisibilityCanBePrivate")
    const val kotlinVersion = "1.9.22"

    @Suppress("MemberVisibilityCanBePrivate")
    const val glideVersion = "4.15.1"

    // Compose Compiler 1.5.10 は jvmTarget = "19" を要求する
    // しかし Android Studio 自体は17で動いてるので単体テスト時に問題がでる
    const val kotlinJvmTarget = "19"
    const val kotlinJvmToolchain = 19

    const val androidGradlePrugin = "8.3.0"

    // const val ankoVersion = "0.10.8"
    // const val commonsCodecVersion = "1.16.0"
    // const val composeVersion = "1.0.5"

    const val androidxActivity = "1.8.2"
    const val androidxAnnotation = "1.7.1"
    const val androidxAppcompat = "1.6.1"
    const val androidxArchCoreTesting = "2.2.0"
    const val androidxComposeRuntime = "1.6.3"
    const val androidxComposeUi = "1.6.3"
    const val androidxComposeMaterialIcons  = "1.6.3"
    const val androidxCore = "1.12.0"
    const val androidxEmoji2 = "1.4.0"
    const val androidxLifecycle = "2.7.0"
    const val androidxMedia3 = "1.3.0"
    const val androidxPreferenceKtx = "1.2.1"
    const val androidxRecyclerView = "1.3.2"
    const val androidxStartup = "1.1.1"
    const val androidxTestCore = "1.5.0"
    const val androidxTestCoreKtx = "1.5.0"
    const val androidxTestEspressoCore = "3.5.1"
    const val androidxTestExtJunit = "1.1.5"
    const val androidxWork = "2.9.0"
    const val apng4AndroidVersion = "2.25.0"
    const val conscryptVersion = "2.5.2"
    const val desugarLibVersion = "2.0.4"
    const val detektVersion = "1.23.5"
    const val gildorkotlinCoroutinesOkhttp = "1.0"
    const val googleFlexbox="3.0.0"
    const val googleMaterial = "1.11.0"
    const val junitVersion = "4.13.2"
    const val koinVersion = "3.5.0"
    const val kotlinxCoroutinesVersion = "1.8.0"
    const val kotlinxSerializationLibVersion = "1.6.3"
    const val kotlinxSerializationPluginVersion = kotlinVersion
    const val kspVersion = "$kotlinVersion-1.0.17"
    const val okhttpVersion = "5.0.0-alpha.12"
    const val webpDecoderVersion = "2.6.$glideVersion"
}
