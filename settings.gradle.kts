pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name="selenus-artemis-kotlin-solana-sdk"
include(":artemis-rpc",":artemis-tx",":artemis-core",":artemis-preview")
include(":artemis-programs")
include(":artemis-metaplex")

include(":artemis-vtx")
include(":artemis-token2022")
include(":artemis-cnft")
include(":artemis-mplcore")
include(":artemis-discriminators")
include(":artemis-ws")
include(":artemis-gaming")
include(":artemis-replay")

include(":artemis-compute")

include(":artemis-wallet")

include(":artemis-logging")

include(":artemis-errors")

include(":artemis-wallet-mwa-android")
include(":artemis-seed-vault")

include(":artemis-nft-compat")

// include(":artemis-compose")

// include(":artemis-react-native")
include(":artemis-candy-machine")
include(":artemis-depin")
include(":artemis-solana-pay")

// v59: optional transaction composer presets (ATA + priority fees + resend)
include(":artemis-tx-presets")

// v60: optional candy machine mint preset built on v58 planner + v59 tx presets
include(":artemis-candy-machine-presets")

// v61: optional preset registry + lightweight interfaces to compose multiple optional modules
include(":artemis-presets")

// v65: comprehensive privacy module for mobile-first privacy features
include(":artemis-privacy")

// v64: optional Android sample app (kept out of default CI/build unless explicitly enabled)
// Usage: ./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
if (providers.gradleProperty("enableAndroidSamples").orNull == "true") {
  include(":samples:solana-mobile-compose-mint-app")
}
// project(":artemis-react-native").projectDir = file("artemis-react-native/android")
