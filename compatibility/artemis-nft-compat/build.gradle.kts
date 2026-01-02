plugins { kotlin("multiplatform") }

kotlin {
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":artemis-core"))
            implementation(project(":artemis-rpc"))
            implementation(project(":artemis-programs"))
            implementation(project(":artemis-tx"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
