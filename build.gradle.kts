plugins {
    alias(libs.plugins.kotlin.jvm) apply false
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
        apply(plugin = "maven-publish")
        apply(plugin = "signing")
        
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                    withJavadocJar()
                }
            }
        }

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

    // Kotlin Multiplatform publishing (for artemis-core)
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pomConfig()
            }
            publishingConfig()
        }

        configure<SigningExtension> { signingConfig(null) }
    }
}
