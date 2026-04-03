plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

kotlin {
    jvm()
    androidTarget()

    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmMain.dependencies {
            implementation(libs.bouncycastle)
        }

        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            runtimeOnly("org.junit.platform:junit-platform-launcher")
        }

        androidMain {
            kotlin.srcDir("src/jvmMain/kotlin")
            dependencies {
                implementation(libs.bouncycastle)
            }
        }
    }
}

android {
    namespace = "com.selenus.artemis.core"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
