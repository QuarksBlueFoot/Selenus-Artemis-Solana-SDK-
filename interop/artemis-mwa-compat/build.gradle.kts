plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.selenus.artemis.interop.mwa"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    api(project(":artemis-wallet-mwa-android"))
    api(project(":artemis-wallet"))
    api(project(":artemis-core"))
    // Re-export the common shim so `com.solana.mobilewalletadapter.common.*`
    // types (ProtocolContract, AssociationContract, SignInWithSolana,
    // NotifyOnCompleteFuture) resolve transitively for consumers of this
    // module, mirroring the upstream ktx -> clientlib -> common chain.
    api(project(":artemis-mwa-common-compat"))
    api(project(":artemis-mwa-clientlib-compat"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
