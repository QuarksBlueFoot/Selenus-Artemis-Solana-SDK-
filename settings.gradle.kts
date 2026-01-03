pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name="selenus-artemis-kotlin-solana-sdk"
include(":artemis-core")
include(":artemis-rpc",":artemis-tx",":artemis-runtime",":artemis-preview")
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

include(":artemis-nft-compat")
include(":artemis-candy-machine")

// v59: optional transaction composer presets (ATA + priority fees + resend)
include(":artemis-tx-presets")

// v60: optional candy machine mint preset built on v58 planner + v59 tx presets
include(":artemis-candy-machine-presets")

// v61: optional preset registry + lightweight interfaces to compose multiple optional modules
include(":artemis-presets")

// v64: optional Android sample app (kept out of default CI/build unless explicitly enabled)
// Usage: ./gradlew -PenableAndroidSamples=true :samples:solana-mobile-compose-mint-app:assembleDebug
if (providers.gradleProperty("enableAndroidSamples").orNull == "true") {
  include(":samples:solana-mobile-compose-mint-app")
}
