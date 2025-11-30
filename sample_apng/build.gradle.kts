plugins {
    id("buildLogic.StAndroidApp")
}

android {
    namespace = "jp.juggler.apng.sample"

    defaultConfig {
        versionCode = 1
        versionName = "1.0"
    }

    packaging {
        resources {
            pickFirsts += listOf("META-INF/atomicfu.kotlin_module")
        }
    }
}

dependencies {
    implementation(project(":base"))
    implementation(project(":apng_android"))
    implementation(libs.androidx.appcompat)

    // ないとなぜかIDE上にエラーが出る
    implementation(libs.androidx.activity.ktx)
}
