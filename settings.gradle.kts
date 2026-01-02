pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "selenus-artemis-kotlin-solana-sdk"

// =============================================================================
// Foundation — core primitives, RPC, transactions, common programs
// These modules must remain stable, lightweight, and dependency-minimal.
// =============================================================================
include(":artemis-core")
include(":artemis-rpc")
include(":artemis-ws")
include(":artemis-tx")
include(":artemis-vtx")
include(":artemis-programs")
include(":artemis-errors")
include(":artemis-logging")
include(":artemis-compute")

project(":artemis-core").projectDir       = file("foundation/artemis-core")
project(":artemis-rpc").projectDir        = file("foundation/artemis-rpc")
project(":artemis-ws").projectDir         = file("foundation/artemis-ws")
project(":artemis-tx").projectDir         = file("foundation/artemis-tx")
project(":artemis-vtx").projectDir        = file("foundation/artemis-vtx")
project(":artemis-programs").projectDir   = file("foundation/artemis-programs")
project(":artemis-errors").projectDir     = file("foundation/artemis-errors")
project(":artemis-logging").projectDir    = file("foundation/artemis-logging")
project(":artemis-compute").projectDir    = file("foundation/artemis-compute")

// =============================================================================
// Mobile — wallet abstraction, MWA, Seed Vault, Compose
// Depends on Foundation only. This is the Solana Mobile Stack replacement layer.
// =============================================================================
include(":artemis-wallet")
include(":artemis-wallet-mwa-android")
include(":artemis-seed-vault")
// include(":artemis-compose")

project(":artemis-wallet").projectDir             = file("mobile/artemis-wallet")
project(":artemis-wallet-mwa-android").projectDir = file("mobile/artemis-wallet-mwa-android")
project(":artemis-seed-vault").projectDir         = file("mobile/artemis-seed-vault")
// project(":artemis-compose").projectDir          = file("mobile/artemis-compose")

// =============================================================================
// Ecosystem — protocol clients and integrations (tokens, NFTs, DeFi)
// Depends on Foundation. Optional for all apps.
// =============================================================================
include(":artemis-token2022")
include(":artemis-metaplex")
include(":artemis-mplcore")
include(":artemis-cnft")
include(":artemis-candy-machine")
include(":artemis-solana-pay")
include(":artemis-anchor")
include(":artemis-jupiter")
include(":artemis-actions")

project(":artemis-token2022").projectDir    = file("ecosystem/artemis-token2022")
project(":artemis-metaplex").projectDir     = file("ecosystem/artemis-metaplex")
project(":artemis-mplcore").projectDir      = file("ecosystem/artemis-mplcore")
project(":artemis-cnft").projectDir         = file("ecosystem/artemis-cnft")
project(":artemis-candy-machine").projectDir = file("ecosystem/artemis-candy-machine")
project(":artemis-solana-pay").projectDir   = file("ecosystem/artemis-solana-pay")
project(":artemis-anchor").projectDir       = file("ecosystem/artemis-anchor")
project(":artemis-jupiter").projectDir      = file("ecosystem/artemis-jupiter")
project(":artemis-actions").projectDir      = file("ecosystem/artemis-actions")

// =============================================================================
// Advanced — power features, experimental modules
// These must never become required for core/mobile adoption.
// =============================================================================
include(":artemis-privacy")
include(":artemis-streaming")
include(":artemis-universal")
include(":artemis-simulation")
include(":artemis-batch")
include(":artemis-scheduler")
include(":artemis-offline")
include(":artemis-portfolio")
include(":artemis-replay")
include(":artemis-gaming")
include(":artemis-depin")
include(":artemis-nlp")
include(":artemis-intent")
include(":artemis-preview")

project(":artemis-privacy").projectDir    = file("advanced/artemis-privacy")
project(":artemis-streaming").projectDir  = file("advanced/artemis-streaming")
project(":artemis-universal").projectDir  = file("advanced/artemis-universal")
project(":artemis-simulation").projectDir = file("advanced/artemis-simulation")
project(":artemis-batch").projectDir      = file("advanced/artemis-batch")
project(":artemis-scheduler").projectDir  = file("advanced/artemis-scheduler")
project(":artemis-offline").projectDir    = file("advanced/artemis-offline")
project(":artemis-portfolio").projectDir  = file("advanced/artemis-portfolio")
project(":artemis-replay").projectDir     = file("advanced/artemis-replay")
project(":artemis-gaming").projectDir     = file("advanced/artemis-gaming")
project(":artemis-depin").projectDir      = file("advanced/artemis-depin")
project(":artemis-nlp").projectDir        = file("advanced/artemis-nlp")
project(":artemis-intent").projectDir     = file("advanced/artemis-intent")
project(":artemis-preview").projectDir    = file("advanced/artemis-preview")

// =============================================================================
// Compatibility / Presets — migration helpers and bundled patterns
// =============================================================================
include(":artemis-discriminators")
include(":artemis-nft-compat")
include(":artemis-tx-presets")
include(":artemis-candy-machine-presets")
include(":artemis-presets")

project(":artemis-discriminators").projectDir       = file("compatibility/artemis-discriminators")
project(":artemis-nft-compat").projectDir           = file("compatibility/artemis-nft-compat")
project(":artemis-tx-presets").projectDir            = file("compatibility/artemis-tx-presets")
project(":artemis-candy-machine-presets").projectDir = file("compatibility/artemis-candy-machine-presets")
project(":artemis-presets").projectDir               = file("compatibility/artemis-presets")

// =============================================================================
// Testing
// =============================================================================
include(":artemis-integration-tests")
include(":artemis-devnet-tests")

project(":artemis-integration-tests").projectDir = file("testing/artemis-integration-tests")
project(":artemis-devnet-tests").projectDir      = file("testing/artemis-devnet-tests")

// =============================================================================
// Samples — opt-in only
// =============================================================================
if (providers.gradleProperty("enableAndroidSamples").orNull == "true") {
    include(":samples:solana-mobile-compose-mint-app")
}
