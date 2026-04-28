// Drop-in source compatibility for `com.metaplex.lib:lib` (metaplex-android).
// Pinned upstream surface: snapshot of github.com/metaplex-foundation/metaplex-android
// `main` as of 2024-04-06 (the upstream repo has been dormant since).
// Coverage is intentionally Partial: the Metaplex entry point + nft /
// tokens / das / candyMachinesV2 / candyMachines accessors are present;
// auctions and the full NFT mutation surface are not.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

extra["upstream.version"] = "main@2024-04-06"
extra["upstream.repo"] = "https://github.com/metaplex-foundation/metaplex-android"
extra["upstream.artifact"] = "com.metaplex.lib:lib"

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api(project(":artemis-core"))
            api(project(":artemis-rpc"))
            api(project(":artemis-tx"))
            api(project(":artemis-metaplex"))
            api(project(":artemis-nft-compat"))
            api(project(":artemis-cnft"))
            api(project(":artemis-mplcore"))
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.interop.metaplexandroid"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
