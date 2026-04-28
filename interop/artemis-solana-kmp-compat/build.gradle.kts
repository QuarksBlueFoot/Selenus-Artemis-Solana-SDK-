// Drop-in source compatibility for `foundation.metaplex.solana-kmp`.
// Pinned upstream surface: snapshot of github.com/metaplex-foundation/solana-kmp
// `main` as of 2024-06-05 (the upstream repo went dormant after that
// commit). Field-level surface: PublicKey, Base58, Amount, Cluster,
// Commitment, Encoding, Rpc*Configuration, Transaction, SolanaMessage,
// SolanaEddsa, SystemProgram, MemoProgram, ReadApiInterface.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

extra["upstream.version"] = "main@2024-06-05"
extra["upstream.repo"] = "https://github.com/metaplex-foundation/solana-kmp"
extra["upstream.artifact"] = "foundation.metaplex:solana-kmp"

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":artemis-core"))
            api(project(":artemis-rpc"))
            api(project(":artemis-tx"))
            api(project(":artemis-vtx"))
            api(project(":artemis-programs"))
            api(project(":artemis-cnft"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.interop.solanakmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
