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
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":artemis-programs"))
            implementation(project(":artemis-tx"))
            implementation(project(":artemis-rpc"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.discriminators"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
