plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}
allprojects{ 
    repositories{ 
        google()
        mavenCentral()
        maven { url = uri("https://repo1.maven.org/maven2/") } // Fallback for 429s
    } 
}

// Per-module POM descriptions. Maven Central requires a non-empty, meaningful
// description on every artifact. Keep entries short (one sentence) and code
// accurate: each string should describe what the module actually ships, not
// what the roadmap promises. When a new publishable module is added, add an
// entry here. The fallback prints the project name so a missing entry is
// noisy in the staging repo rather than silent in Central.
val artemisModuleDescriptions: Map<String, String> = mapOf(
    // Foundation
    "artemis-core" to "Core Solana primitives: PublicKey, Keypair, base58, Ed25519, commitment levels, and shared types used by every Artemis module.",
    "artemis-rpc" to "Solana JSON-RPC client over Ktor with full method coverage, retry and backoff, rate-limit handling, and structured error mapping.",
    "artemis-ws" to "Solana WebSocket subscriptions (accountSubscribe, signatureSubscribe, programLogsSubscribe) with deterministic reconnect, replay, and epoch counters.",
    "artemis-tx" to "Legacy transaction builder, signer, and serializer for classic Solana message format.",
    "artemis-vtx" to "Versioned transaction (v0) builder with address lookup table support, compiled instructions, and deterministic wire encoding.",
    "artemis-programs" to "Typed instruction builders for System, SPL Token, Stake, Vote, Memo, BPF Loader, and Address Lookup Table programs.",
    "artemis-errors" to "Structured Solana error taxonomy: program errors, RPC errors, simulation errors, and user-actionable error messages.",
    "artemis-logging" to "Pluggable logging facade with sinks for console, file, Logcat, and OSLog, plus redaction for secrets and pubkeys.",
    "artemis-compute" to "Compute budget instruction builders: SetComputeUnitLimit, SetComputeUnitPrice, RequestHeapFrame, and priority-fee helpers.",

    // Mobile
    "artemis-wallet" to "Wallet abstraction with local in-memory signers and injectable WalletAdapter/Signer backends. Platform agnostic entry point for mobile wallet flows.",
    "artemis-wallet-mwa-android" to "Mobile Wallet Adapter 2.0 client for Android: connect, reauthorize, sign-in with Solana, sign transactions, sign messages, and batch send.",
    "artemis-seed-vault" to "Solana Mobile Seed Vault integration: authorization, key discovery, transaction signing, and permission lifecycle management.",

    // Ecosystem
    "artemis-token2022" to "SPL Token-2022 client with extensions: transfer fee, interest bearing, default account state, metadata pointer, and confidential transfer scaffolding.",
    "artemis-metaplex" to "Metaplex Token Metadata client: create and update metadata accounts, verify collections, master-edition instructions, and fetch on-chain + off-chain JSON metadata.",
    "artemis-mplcore" to "Metaplex MPL-Core asset client: create, update, transfer, burn, and plugin management for the new core NFT standard.",
    "artemis-cnft" to "Compressed NFT support: DAS (Digital Asset Standard) API client, Merkle proof verification, and transfer instruction builders.",
    "artemis-candy-machine" to "Candy Machine v3 client: initialize, mint, update guards, and fetch state for Metaplex candy machines.",
    "artemis-solana-pay" to "Solana Pay URL builder and parser: transfer requests, transaction requests, reference tracking, and validation.",
    "artemis-anchor" to "Anchor-compatible client: IDL parsing, account deserialization, instruction encoding with 8-byte discriminators, and event decoding.",
    "artemis-jupiter" to "Jupiter aggregator client (v6): quote, swap, streaming quote updates, route visualization, and price-impact analysis.",
    "artemis-actions" to "Solana Actions and Blinks client: parse action URLs, fetch and execute action endpoints, actions.json manifest lookup, QR code rendering, and deep-link helpers.",

    // Advanced
    "artemis-privacy" to "Privacy primitives: stealth addresses, ring signatures, encrypted memos, X25519 key exchange, Shamir secret sharing, and mixing-pool helpers.",
    "artemis-streaming" to "Zero-copy account streaming: direct-buffer field reads, delta detection, ring-buffered history, and backpressure-aware Flows for real-time account updates on mobile without GC pressure.",
    "artemis-universal" to "Universal program client that calls any Solana program without an IDL via runtime discriminator discovery, account pattern recognition, and schema caching.",
    "artemis-simulation" to "Predictive transaction analysis: simulation, compute-unit estimation, success-probability scoring, confirmation-time prediction, MEV vulnerability detection, and optimal submission timing.",
    "artemis-batch" to "Batch transaction orchestration with per-transaction result tracking (success, failure, signed-but-not-broadcast) and XOR invariants.",
    "artemis-scheduler" to "Predictive transaction scheduler that picks submission slots based on recent block production and fee pressure.",
    "artemis-offline" to "Offline transaction queue with pluggable storage and a TransactionSubmitter that drains the queue when connectivity returns.",
    "artemis-portfolio" to "Wallet portfolio tracking: RPC-based balance fetcher, in-memory portfolio state, asset model, and a WebSocket-driven live tracker with debounced updates.",
    "artemis-replay" to "Deterministic replay recorder and player: capture game frames and instructions, then replay for bug reproduction, desync debugging, and anti-cheat telemetry.",
    "artemis-gaming" to "Gaming primitives: session keys, adaptive priority-fee optimizer, address lookup table session builder and executor, Merkle reward distribution, verifiable randomness, and game state proofs.",
    "artemis-depin" to "DePIN helpers: device attestation, device identity, and batched telemetry submission for decentralized physical infrastructure workloads.",
    "artemis-nlp" to "Natural-language transaction builder: parse instructions like 'send 1 SOL to X' into executable transactions, with on-chain entity resolution for symbols and names.",
    "artemis-intent" to "Transaction intent decoder: parses SystemProgram, SPL Token, Token-2022, Stake, Memo, ATA, and Compute Budget instructions back into typed `TransactionIntent` records via a pluggable `ProgramRegistry`.",
    "artemis-preview" to "Human-readable transaction preview driven by on-chain simulation: decodes instructions and summarizes expected effects.",

    // Compatibility / Presets
    "artemis-discriminators" to "Shared Anchor discriminator catalog: precomputed 8-byte tags for common Anchor instructions and account types.",
    "artemis-nft-compat" to "Token Metadata Borsh parsers: decode on-chain Metadata, MasterEdition, and CollectionAuthorityRecord accounts into typed models.",
    "artemis-tx-presets" to "Curated transaction presets: common SOL and SPL flows preassembled with sane defaults for quick integration.",
    "artemis-candy-machine-presets" to "Candy Machine configuration presets covering the common mint flows (public sale, allowlist, gated).",
    "artemis-presets" to "Top-level preset bundle that pulls in transaction and candy-machine presets under one import.",

    // Interop / compat shims
    "artemis-seedvault-compat" to "Drop-in replacement for com.solanamobile:seedvault-wallet-sdk: same package and class names, routed to Artemis Seed Vault.",
    "artemis-mwa-compat" to "Drop-in replacement for com.solanamobile:mobile-wallet-adapter-clientlib: mirrors the upstream client API backed by Artemis MWA.",
    "artemis-mwa-clientlib-compat" to "Legacy package alias for the MWA clientlib shim. Pulled in automatically for apps that reference the older Gradle coordinate.",
    "artemis-mwa-common-compat" to "Shared MWA protocol types (Account, AuthorizationResult, SignInPayload) exposed under the upstream package for source compatibility.",
    "artemis-sol4k-compat" to "Sol4k source-compat shim: exposes sol4k API shapes (Connection, Keypair, PublicKey, Transaction) backed by Artemis.",
    "artemis-solana-kmp-compat" to "solana-kmp source-compat shim for projects migrating from the Solana KMP fork.",
    "artemis-metaplex-android-compat" to "metaplex-android source-compat shim: routes Metaplex calls through artemis-metaplex.",
    "artemis-web3-solana-compat" to "web3-solana source-compat shim that mirrors the small web3-solana Android surface on top of Artemis.",
    "artemis-rpc-core-compat" to "rpc-core source-compat shim that exposes the upstream rpc-core API backed by artemis-rpc."
)

subprojects {
    if (project.name == "artemis-react-native") return@subprojects
    if (project.name == "artemis-integration-tests") return@subprojects
    if (project.name == "artemis-devnet-tests") return@subprojects

    val pomConfig: MavenPublication.() -> Unit = {
        pom {
            name.set(project.name)
            val moduleDescription = artemisModuleDescriptions[project.name]
                ?: "Artemis Solana SDK module: ${project.name}"
            description.set(moduleDescription)
            url.set("https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("QuarksBlueFoot")
                    name.set("Bluefoot Labs")
                    email.set("contact@bluefootlabs.com")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-.git")
                developerConnection.set("scm:git:ssh://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-.git")
                url.set("https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-")
            }
        }
    }

    val publishingConfig: PublishingExtension.() -> Unit = {
        repositories {
            // Local staging mirror — always written. Useful for inspecting the
            // artifacts that WILL be uploaded (`build/staging-deploy/...`)
            // before trusting a remote publish.
            maven {
                name = "LocalStaging"
                url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
            }

            // Sonatype OSSRH staging endpoint exposed by the new Central
            // Portal. Publishing here drops the artifacts into the namespace
            // `xyz.selenus` staging bucket; the publish workflow then POSTs
            // to `/manual/upload/defaultRepository/xyz.selenus` to promote
            // from staging to Central. Credentials match the ones on
            // https://central.sonatype.com/account (CENTRAL_USERNAME /
            // CENTRAL_PASSWORD). Skipped when creds aren't set so local
            // `./gradlew publish` still works offline.
            val centralUser = findProperty("CENTRAL_USERNAME") as String?
                ?: System.getenv("CENTRAL_USERNAME")
            val centralPass = findProperty("CENTRAL_PASSWORD") as String?
                ?: System.getenv("CENTRAL_PASSWORD")
            if (!centralUser.isNullOrBlank() && !centralPass.isNullOrBlank()) {
                maven {
                    name = "CentralPortalStaging"
                    url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                    credentials {
                        username = centralUser
                        password = centralPass
                    }
                }
            }
        }
    }

    val signingConfig: SigningExtension.(String?) -> Unit = { pubName ->
        val signingKeyId = findProperty("signing.keyId") as String? ?: System.getenv("SIGNING_KEY_ID")
        val signingKey = System.getenv("SIGNING_KEY")
        val signingPassword = findProperty("signing.password") as String? ?: System.getenv("SIGNING_PASSWORD")
        isRequired = !signingKey.isNullOrEmpty() || System.getenv("GPG_SIGNING") == "true"
        if (!signingKey.isNullOrEmpty()) {
            useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        } else if (System.getenv("GPG_SIGNING") == "true") {
            useGpgCmd()
        }
        if (isRequired) {
            if (pubName != null) {
                sign(extensions.getByType<PublishingExtension>().publications[pubName])
            } else {
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }

        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        configure<JavaPluginExtension> {
            withJavadocJar()
            withSourcesJar()
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pomConfig()
                }
            }
            publishingConfig()
        }

        configure<SigningExtension> { signingConfig("maven") }
    }

    plugins.withId("com.android.library") {
        // For standalone Android modules (mwa-android, seed-vault), configure publishing.
        // KMP+Android modules get publishing from the multiplatform block instead.
        val isKmp = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")

        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                    withJavadocJar()
                }
            }
        }

        if (!isKmp) {
            apply(plugin = "maven-publish")
            apply(plugin = "signing")

            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        afterEvaluate {
                            from(components["release"])
                            pomConfig()
                        }
                    }
                }
                publishingConfig()
            }

            configure<SigningExtension> { signingConfig("maven") }
        }
    }

    // Kotlin Multiplatform publishing
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            jvmToolchain(17)
        }

        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        // Maven Central rejects components that don't ship a javadoc jar. KMP
        // target publications (common metadata, jvm, native) don't get one by
        // default, so Central Portal drops the deployment with:
        //   "Javadocs must be provided but not found in entries"
        // Attach an empty `-javadoc.jar` — signed, valid, empty content — to
        // every publication that doesn't already have one. The Android
        // release variant ships its own via LibraryExtension#singleVariant;
        // skipping it here avoids a duplicate-classifier conflict. A
        // Dokka-generated jar would be strictly better but adds weight on
        // every KMP module for no real signal beyond the sources jar.
        val javadocJar = tasks.register<Jar>("emptyJavadocJar") {
            archiveClassifier.set("javadoc")
        }

        configure<PublishingExtension> {
            // Attach the empty javadoc jar to every KMP publication — the
            // root metadata ("kotlinMultiplatform"), target publications
            // ("jvm", "androidRelease", native variants). None of them
            // generate a javadoc jar on their own in a KMP build, including
            // the Android release variant (LibraryExtension#singleVariant's
            // withJavadocJar() only wires through when the publication is
            // created via components["release"], not via KMP's own
            // publishLibraryVariants hook).
            publications.withType<MavenPublication>().configureEach {
                pomConfig()
                artifact(javadocJar)
            }
            publishingConfig()
        }

        configure<SigningExtension> { signingConfig(null) }

        // For KMP modules with androidTarget(): publish release variant
        afterEvaluate {
            extensions.findByType<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>()
                ?.targets
                ?.filterIsInstance<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget>()
                ?.forEach { it.publishLibraryVariants("release") }
        }
    }
}

// =============================================================================
// Compat-module API surface snapshot
//
// Dumps a stable text-format .api file per compat module. Committed snapshots
// under `interop/<module>/api/` let CI diff the current public surface against
// the last approved one. Catches accidental breaking changes on compat
// modules without requiring the binary-compat-validator plugin (which has
// patchy KMP/Android-hybrid support).
//
// Run with: ./gradlew dumpApi
// Run per-module with: ./gradlew :artemis-sol4k-compat:dumpApi
// =============================================================================
tasks.register("dumpApi") {
    group = "verification"
    description = "Dumps a text-format .api surface snapshot for every compat module"

    val compatModules = listOf(
        "artemis-mwa-compat",
        "artemis-mwa-clientlib-compat",
        "artemis-mwa-common-compat",
        "artemis-seedvault-compat",
        "artemis-sol4k-compat",
        "artemis-web3-solana-compat",
        "artemis-rpc-core-compat",
        "artemis-solana-kmp-compat",
        "artemis-metaplex-android-compat"
    )

    doLast {
        compatModules.forEach { mod ->
            val module = rootProject.findProject(":$mod") ?: return@forEach
            val src = file("interop/$mod/src")
            if (!src.exists()) return@forEach
            val out = file("interop/$mod/api/$mod.api")
            out.parentFile.mkdirs()
            val lines = mutableListOf<String>()
            lines += "# API surface for $mod"
            lines += "# Generated by ./gradlew dumpApi. Commit this file; CI diffs against it."
            lines += ""
            // Sort by invariantSeparatorsPath so file ordering is identical
            // across Windows (backslash paths) and Linux (slash paths). Without
            // this, the dumpApi output drifts between developer machines and
            // the CI runner, breaking the API surface diff gate.
            src.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .sortedBy { it.relativeTo(src).invariantSeparatorsPath }
                .forEach { ktFile ->
                    val relPath = ktFile.relativeTo(src).invariantSeparatorsPath
                    val packageLine = ktFile.useLines { seq ->
                        seq.firstOrNull { it.startsWith("package ") }
                    }?.removePrefix("package ")?.trim() ?: "<unknown>"
                    val publicDecls = ktFile.useLines { seq ->
                        seq.mapIndexedNotNull { idx, line ->
                            val trimmed = line.trimStart()
                            val isPublic = !trimmed.startsWith("private ") &&
                                !trimmed.startsWith("internal ") &&
                                !trimmed.startsWith("protected ") &&
                                !trimmed.startsWith("//") &&
                                !trimmed.startsWith("*") &&
                                !trimmed.startsWith("/*")
                            val isDecl = trimmed.startsWith("class ") ||
                                trimmed.startsWith("interface ") ||
                                trimmed.startsWith("object ") ||
                                trimmed.startsWith("sealed class ") ||
                                trimmed.startsWith("sealed interface ") ||
                                trimmed.startsWith("enum class ") ||
                                trimmed.startsWith("data class ") ||
                                trimmed.startsWith("abstract class ") ||
                                trimmed.startsWith("open class ") ||
                                trimmed.startsWith("typealias ") ||
                                trimmed.startsWith("fun ") ||
                                trimmed.startsWith("suspend fun ") ||
                                trimmed.startsWith("inline fun ") ||
                                trimmed.startsWith("val ") ||
                                trimmed.startsWith("var ") ||
                                trimmed.startsWith("const val ")
                            if (isPublic && isDecl) {
                                "  L${idx + 1}  $trimmed"
                            } else null
                        }.toList()
                    }
                    if (publicDecls.isNotEmpty()) {
                        lines += "## $relPath (package $packageLine)"
                        lines.addAll(publicDecls)
                        lines += ""
                    }
                }
            out.writeText(lines.joinToString("\n"))
            println("Dumped API surface for $mod -> ${out.relativeTo(rootDir).invariantSeparatorsPath}")
        }
    }
}

// =============================================================================
// Dependency Ring Enforcement
// Verifies that no module depends on a higher ring than allowed.
// Run with: ./gradlew checkDependencyRings
// =============================================================================
tasks.register("checkDependencyRings") {
    group = "verification"
    description = "Checks that module dependencies respect the ring hierarchy"

    doLast {
        val foundation = setOf(
            "artemis-core", "artemis-rpc", "artemis-ws", "artemis-tx", "artemis-vtx",
            "artemis-programs", "artemis-errors", "artemis-logging", "artemis-compute"
        )
        val mobile = setOf("artemis-wallet", "artemis-wallet-mwa-android", "artemis-seed-vault", "artemis-compose")
        val ecosystem = setOf(
            "artemis-token2022", "artemis-metaplex", "artemis-mplcore", "artemis-cnft",
            "artemis-candy-machine", "artemis-solana-pay", "artemis-anchor", "artemis-jupiter", "artemis-actions"
        )
        val advanced = setOf(
            "artemis-privacy", "artemis-streaming", "artemis-universal", "artemis-simulation",
            "artemis-batch", "artemis-scheduler", "artemis-offline", "artemis-portfolio",
            "artemis-replay", "artemis-gaming", "artemis-depin", "artemis-nlp", "artemis-intent", "artemis-preview"
        )
        val compat = setOf(
            "artemis-discriminators", "artemis-nft-compat", "artemis-tx-presets",
            "artemis-candy-machine-presets", "artemis-presets"
        )
        val interop = setOf(
            "artemis-seedvault-compat", "artemis-mwa-compat"
        )
        val testing = setOf("artemis-integration-tests", "artemis-devnet-tests")

        fun ringOf(name: String): Int = when (name) {
            in foundation -> 1
            in mobile -> 2
            in ecosystem -> 3
            in advanced -> 4
            in compat -> 5
            in interop -> 6
            in testing -> 99
            else -> -1
        }

        fun ringName(ring: Int): String = when (ring) {
            1 -> "Foundation"
            2 -> "Mobile"
            3 -> "Ecosystem"
            4 -> "Advanced"
            5 -> "Compat"
            6 -> "Interop"
            99 -> "Testing"
            else -> "Unknown"
        }

        // Rules: each ring can only depend on rings <= its own number
        // Exception: Compat (5) cannot depend on Advanced (4)
        // Exception: Interop (6) may depend on Foundation, Mobile, Ecosystem only
        // Exception: Testing (99) can depend on anything
        //
        // Explicit, narrow allowlist for deliberate bundler edges. Each entry
        // is a `sub -> dep` pair that is permitted despite the default ring
        // rules. The comment on every entry documents why.
        val allowedEdges: Set<Pair<String, String>> = setOf(
            // ArtemisMobile is the documented convenience entry point for
            // apps that want a full mobile stack in one object: RPC, wallet,
            // realtime, and NFT helpers. It lives in the MWA wallet module
            // (ring Mobile) and bundles in :artemis-cnft (ring Ecosystem) so
            // callers don't have to wire five modules by hand. Moving the
            // bundler to its own module would be the purest fix; until that
            // refactor, this edge is explicitly allowed.
            "artemis-wallet-mwa-android" to "artemis-cnft"
        )

        val violations = mutableListOf<String>()

        subprojects.forEach { sub ->
            val subRing = ringOf(sub.name)
            if (subRing == -1 || subRing == 99) return@forEach

            sub.configurations
                .filter { it.name == "implementation" || it.name == "api" }
                .forEach { config ->
                    config.dependencies
                        .filterIsInstance<ProjectDependency>()
                        .forEach { dep ->
                            val depRing = ringOf(dep.dependencyProject.name)
                            if (depRing == -1) return@forEach

                            val forbidden = when {
                                (sub.name to dep.dependencyProject.name) in allowedEdges -> false
                                subRing == 5 && depRing == 4 -> true  // Compat cannot depend on Advanced
                                subRing == 6 && depRing == 4 -> true  // Interop cannot depend on Advanced
                                subRing == 6 && depRing == 5 -> true  // Interop cannot depend on Compat
                                subRing == 4 && depRing == 5 -> false // Advanced may use Compat utilities
                                depRing > subRing -> true             // Lower ring depending on higher ring
                                else -> false
                            }

                            if (forbidden) {
                                violations.add(
                                    "❌ ${sub.name} (${ringName(subRing)}) -> ${dep.dependencyProject.name} (${ringName(depRing)})"
                                )
                            }
                        }
                }
        }

        if (violations.isNotEmpty()) {
            violations.forEach { println(it) }
            throw GradleException("Found ${violations.size} dependency ring violation(s):\n${violations.joinToString("\n")}")
        } else {
            println("✅ All ${subprojects.size} modules respect the ring dependency hierarchy")
        }
    }
}
