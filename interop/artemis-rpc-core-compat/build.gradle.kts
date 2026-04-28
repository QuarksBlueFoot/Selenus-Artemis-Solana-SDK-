// Drop-in source compatibility for `com.solana:rpc-core` (solana-mobile/rpc-core).
// Pinned upstream surface: snapshot of github.com/solana-mobile/rpc-core `main`
// as of 2026-01-09. Includes JsonRpc envelope types, SolanaRpcClient, AccountInfo,
// SolanaResponse, and TransactionOptions. KtorNetworkDriver / OkioNetworkDriver
// not yet ported; consumers wire ArtemisHttpNetworkDriver instead.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

extra["upstream.version"] = "main@2026-01-09"
extra["upstream.repo"] = "https://github.com/solana-mobile/rpc-core"
extra["upstream.artifact"] = "com.solana:rpc-core"

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":artemis-core"))
            api(project(":artemis-rpc"))
            api(project(":artemis-tx"))
            api(project(":artemis-web3-solana-compat"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.interop.rpccore"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
