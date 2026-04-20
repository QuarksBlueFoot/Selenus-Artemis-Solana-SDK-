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

subprojects {
    if (project.name == "artemis-react-native") return@subprojects
    if (project.name == "artemis-integration-tests") return@subprojects
    if (project.name == "artemis-devnet-tests") return@subprojects

    val pomConfig: MavenPublication.() -> Unit = {
        pom {
            name.set(project.name)
            description.set("Artemis Solana SDK module: ${project.name}")
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
            maven {
                name = "Staging"
                url = uri(layout.buildDirectory.dir("../../build/staging-deploy"))
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

        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pomConfig()
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
            src.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .sorted()
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
