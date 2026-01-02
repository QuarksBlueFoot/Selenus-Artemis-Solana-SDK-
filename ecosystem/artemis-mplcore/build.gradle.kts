plugins { kotlin("multiplatform") }

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-discriminators"))
            implementation(project(":artemis-core"))
            implementation(project(":artemis-tx"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
