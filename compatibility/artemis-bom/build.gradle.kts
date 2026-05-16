plugins {
    `java-platform`
    `maven-publish`
    signing
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":artemis-core"))
        api(project(":artemis-rpc"))
        api(project(":artemis-ws"))
        api(project(":artemis-tx"))
        api(project(":artemis-vtx"))
        api(project(":artemis-programs"))
        api(project(":artemis-errors"))
        api(project(":artemis-logging"))
        api(project(":artemis-compute"))

        api(project(":artemis-wallet"))
        api(project(":artemis-wallet-mwa-android"))
        api(project(":artemis-wallet-mwa-walletlib-android"))
        api(project(":artemis-seed-vault"))

        api(project(":artemis-token2022"))
        api(project(":artemis-metaplex"))
        api(project(":artemis-mplcore"))
        api(project(":artemis-cnft"))
        api(project(":artemis-candy-machine"))
        api(project(":artemis-solana-pay"))
        api(project(":artemis-anchor"))
        api(project(":artemis-jupiter"))
        api(project(":artemis-actions"))

        api(project(":artemis-discriminators"))
        api(project(":artemis-nft-compat"))
        api(project(":artemis-tx-presets"))
        api(project(":artemis-candy-machine-presets"))
        api(project(":artemis-presets"))

        api(project(":artemis-seedvault-compat"))
        api(project(":artemis-mwa-compat"))
        api(project(":artemis-mwa-common-compat"))
        api(project(":artemis-mwa-clientlib-compat"))
        api(project(":artemis-mwa-walletlib-compat"))
        api(project(":artemis-sol4k-compat"))
        api(project(":artemis-solana-kmp-compat"))
        api(project(":artemis-web3-solana-compat"))
        api(project(":artemis-rpc-core-compat"))
        api(project(":artemis-metaplex-android-compat"))
        api(project(":artemis-multimult-compat"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            pom {
                name.set(project.name)
                description.set("Maven BOM that aligns Artemis Solana SDK module versions.")
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
    }
    repositories {
        maven {
            name = "LocalStaging"
            url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
        }

        val centralUser = findProperty("CENTRAL_USERNAME") as String? ?: System.getenv("CENTRAL_USERNAME")
        val centralPass = findProperty("CENTRAL_PASSWORD") as String? ?: System.getenv("CENTRAL_PASSWORD")
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

signing {
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
        sign(publishing.publications["maven"])
    }
}