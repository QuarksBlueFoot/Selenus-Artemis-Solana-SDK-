plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.selenus.artemis.interop.mwawalletlib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = 23 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests {
            // `android.net.Uri` is resolved by the stub jar on the
            // unit-test classpath. Default values keep the parser
            // tests off Robolectric.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(project(":artemis-core"))
    api(project(":artemis-wallet"))
    api(project(":artemis-wallet-mwa-android"))
    api(project(":artemis-wallet-mwa-walletlib-android"))
    api(project(":artemis-mwa-common-compat"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
