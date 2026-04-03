plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-discriminators"))
            implementation(project(":artemis-core"))
            implementation(project(":artemis-tx"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.mplcore"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
