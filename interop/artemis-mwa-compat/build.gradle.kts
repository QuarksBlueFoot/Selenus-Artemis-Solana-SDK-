// Drop-in source compatibility for `com.solana.mobilewalletadapter:clientlib-ktx`.
// Pinned upstream surface: 1.4.3 (github.com/solana-mobile/mobile-wallet-adapter
// tag `v1.4.3`). The `dumpApi` task in the root build.gradle.kts diffs our
// surface against `api/artemis-mwa-compat.api`; bump that snapshot AND this
// pin together when upstream releases.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

extra["upstream.version"] = "1.4.3"
extra["upstream.repo"] = "https://github.com/solana-mobile/mobile-wallet-adapter"
extra["upstream.artifact"] = "com.solana.mobilewalletadapter:clientlib-ktx"

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

    testOptions {
        unitTests {
            // `android.net.Uri` resolves to the JVM stub on the unit-test
            // classpath; returning default values lets the AuthorizationResult
            // / TransactionResult invariants compile without Robolectric.
            isReturnDefaultValues = true
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

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.mockk)
}
