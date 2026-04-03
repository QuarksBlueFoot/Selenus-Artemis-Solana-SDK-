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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain {
            kotlin.srcDir("src/jvmMain/kotlin")
        }
    }
}

android {
    namespace = "com.selenus.artemis.logging"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
