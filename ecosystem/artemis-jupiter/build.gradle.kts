plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-core"))
            implementation(project(":artemis-tx"))
            implementation(project(":artemis-rpc"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(libs.okhttp)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
        }
        androidMain {
            kotlin.srcDir("src/jvmMain/kotlin")
            dependencies {
                implementation(libs.okhttp)
            }
        }
    }
}

android {
    namespace = "com.selenus.artemis.jupiter"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
