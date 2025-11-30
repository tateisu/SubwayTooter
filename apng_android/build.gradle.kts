plugins {
    id("buildLogic.StAndroidLib")
}

android {
    namespace = "jp.juggler.apng"
}

dependencies {
    api(project(":apng"))
    implementation(project(":base"))

    implementation(libs.glide)
    implementation(libs.webpDecoder)
}
