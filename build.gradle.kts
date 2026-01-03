plugins{ kotlin("jvm") version "2.0.21" apply false
  id("com.android.library") version "8.7.3" apply false
  id("com.android.application") version "8.7.3" apply false
  id("org.jetbrains.kotlin.android") version "2.0.21" apply false
  kotlin("plugin.serialization") version "2.0.21" apply false
}
allprojects{ repositories{ google(); mavenCentral() } }

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "maven-publish")
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
        }
    }

    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")
        
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    afterEvaluate {
                        from(components["release"])
                    }
                }
            }
        }
    }
}
