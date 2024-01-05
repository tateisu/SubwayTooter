import org.gradle.api.JavaVersion

@Suppress("MemberVisibilityCanBePrivate")
object Vers {
    const val stBuildToolsVersion = "34.0.0"
    const val stCompileSdkVersion = 34
    const val stTargetSdkVersion = 34
    const val stMinSdkVersion = 26

    val javaSourceCompatibility = JavaVersion.VERSION_1_8
    val javaTargetCompatibility = JavaVersion.VERSION_1_8

    const val kotlinVersion = "1.9.22"
    const val kotlinJvmTarget = "1.8"
    const val kotlinJvmToolchain = 17

    const val androidGradlePruginVersion = "8.2.1"

    const val androidxAnnotationVersion = "1.6.0"
    const val androidxTestEspressoCoreVersion = "3.5.1"
    const val androidxTestExtJunitVersion = "1.1.5"
    const val androidxTestVersion = "1.5.0"

    // const val ankoVersion = "0.10.8"
    const val appcompatVersion = "1.6.1"
    const val archVersion = "2.2.0"
    const val commonsCodecVersion = "1.16.0"
    const val composeVersion = "1.0.5"
    const val conscryptVersion = "2.5.2"
    const val coreKtxVersion = "1.12.0"
    const val desugarLibVersion = "2.0.4"
    const val detektVersion = "1.23.4"
    const val emoji2Version = "1.4.0"
    const val glideVersion = "4.15.1"
    const val junitVersion = "4.13.2"
    const val koinVersion = "3.5.0"
    const val kotlinTestVersion = kotlinVersion // "1.9.22"
    const val kotlinxCoroutinesVersion = "1.7.3"
    const val kotlinxSerializationPluginVersion = kotlinVersion
    const val kotlinxSerializationLibVersion = "1.6.2"
    const val kspVersion = "$kotlinVersion-1.0.16"
    const val lifecycleVersion = "2.6.2"
    const val materialVersion = "1.11.0"
    const val media3Version = "1.2.0"
    const val okhttpVersion = "5.0.0-alpha.11"
    const val preferenceKtxVersion = "1.2.1"
    const val startupVersion = "1.1.1"
    const val testKtxVersion = "1.5.0"
    const val webpDecoderVersion = "2.6.$glideVersion"
    const val workVersion = "2.9.0"
}
