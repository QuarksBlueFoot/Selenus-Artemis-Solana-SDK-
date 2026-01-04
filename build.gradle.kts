plugins{ kotlin("jvm") version "2.0.21" apply false
  id("com.android.library") version "8.7.3" apply false
  id("com.android.application") version "8.7.3" apply false
  id("org.jetbrains.kotlin.android") version "2.0.21" apply false
  kotlin("plugin.serialization") version "2.0.21" apply false
}
allprojects{ 
    repositories{ 
        google()
        mavenCentral()
        maven { url = uri("https://repo1.maven.org/maven2/") } // Fallback for 429s
    } 
}

subprojects {
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
                url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
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

        configure<SigningExtension> {
            val signingKey = System.getenv("SIGNING_KEY")
            val signingPassword = System.getenv("SIGNING_PASSWORD")
            if (!signingKey.isNullOrEmpty()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications["maven"])
            }
        }
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

        configure<SigningExtension> {
            val signingKey = System.getenv("SIGNING_KEY")
            val signingPassword = System.getenv("SIGNING_PASSWORD")
            if (!signingKey.isNullOrEmpty()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications["maven"])
            }
        }
    }
}
