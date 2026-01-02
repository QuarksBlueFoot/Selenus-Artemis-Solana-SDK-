plugins { kotlin("multiplatform") }

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(project(":artemis-core"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":artemis-programs"))
            implementation(project(":artemis-tx"))
            implementation(project(":artemis-rpc"))
        }
    }
}
