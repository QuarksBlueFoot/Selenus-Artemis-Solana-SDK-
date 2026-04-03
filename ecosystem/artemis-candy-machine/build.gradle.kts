plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-core"))
            implementation(project(":artemis-tx"))
            implementation(project(":artemis-programs"))
            implementation(project(":artemis-discriminators"))
            implementation(project(":artemis-nft-compat"))
            implementation(project(":artemis-rpc"))
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.candymachine"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
