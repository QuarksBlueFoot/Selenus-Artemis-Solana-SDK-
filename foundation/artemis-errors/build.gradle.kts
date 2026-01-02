plugins { kotlin("multiplatform") }

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-logging"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
