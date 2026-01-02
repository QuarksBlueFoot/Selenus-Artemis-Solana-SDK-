plugins { kotlin("multiplatform") }

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-core"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
