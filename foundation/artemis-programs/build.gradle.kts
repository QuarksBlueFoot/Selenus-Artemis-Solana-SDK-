plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain { dependencies {
            implementation(project(":artemis-core"))
            implementation(project(":artemis-rpc"))
            implementation(project(":artemis-tx"))
        }}
        commonTest { dependencies { implementation(kotlin("test")) }}
    }
}

android {
    namespace = "com.selenus.artemis.programs"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
