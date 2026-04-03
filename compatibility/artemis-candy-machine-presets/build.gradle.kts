plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-rpc"))
            implementation(project(":artemis-wallet"))
            implementation(project(":artemis-tx"))
            implementation(project(":artemis-programs"))
            implementation(project(":artemis-errors"))
            implementation(project(":artemis-candy-machine"))
            implementation(project(":artemis-tx-presets"))
            implementation(project(":artemis-presets"))
            implementation(project(":artemis-core"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.candymachinepresets"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
