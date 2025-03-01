plugins {
    id("buildLogic.StAndroidLib")
}

android {
    namespace = "jp.juggler.anko"
}

dependencies {
    implementation(project(":base"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.google.material)

    testImplementation(libs.kotlin.test)

    androidTestRuntimeOnly(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
