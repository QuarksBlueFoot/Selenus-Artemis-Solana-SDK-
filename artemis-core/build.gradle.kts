plugins {
    kotlin("multiplatform")
    id("org.jetbrains.dokka") version "1.9.20"
}

kotlin {
    jvm {
        // Generate javadoc JAR for Maven Central compliance
        withSourcesJar()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            // Coroutines for Flow-based reactive APIs and concurrency
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}

// Create javadoc JAR from Dokka for Maven Central
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

// Attach javadoc JAR to JVM publication
publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "jvm") {
            artifact(dokkaJavadocJar)
        }
    }
}
