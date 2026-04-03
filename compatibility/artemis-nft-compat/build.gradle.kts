plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":artemis-core"))
            implementation(project(":artemis-rpc"))
            implementation(project(":artemis-programs"))
            implementation(project(":artemis-tx"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.nftcompat"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
