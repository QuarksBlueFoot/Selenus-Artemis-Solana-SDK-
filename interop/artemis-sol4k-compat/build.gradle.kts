// Drop-in source compatibility for `org.sol4k:sol4k`.
// Pinned upstream surface: sol4k 0.7.0 (tag `0.7.0`, commit on the
// `main` branch of github.com/sol4k/sol4k as of the audit date).
// Update both this comment and the API snapshot at
// `api/artemis-sol4k-compat.api` whenever upstream cuts a new release;
// CI's `dumpApi` drift check will fail the build until they agree.
plugins {
    kotlin("multiplatform")
    id("com.android.library")
}

extra["upstream.version"] = "0.7.0"
extra["upstream.repo"] = "https://github.com/sol4k/sol4k"

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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.selenus.artemis.interop.sol4k"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
