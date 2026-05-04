// Drop-in source compatibility for `com.solana:web3-solana` (Funkatronics/web3-solana).
// Pinned upstream surface: snapshot of github.com/Funkatronics/web3-solana `main`
// as of 2025-08 (last verified). Surfaces SolanaPublicKey, ProgramDerivedAddress,
// Transaction, Message, Builder, AccountMeta, Instruction, SolanaSigner, and
// the Artemis-backed program helpers listed in PARITY_MATRIX.md. TokenProgram
// setAuthority/freeze/thaw remain native gaps and are not claimed by this shim.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

extra["upstream.version"] = "main@2025-08"
extra["upstream.repo"] = "https://github.com/Funkatronics/web3-solana"
extra["upstream.artifact"] = "com.solana:web3-solana"

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":artemis-core"))
            api(project(":artemis-tx"))
            api(project(":artemis-vtx"))
            api(project(":artemis-programs"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.interop.web3solana"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
