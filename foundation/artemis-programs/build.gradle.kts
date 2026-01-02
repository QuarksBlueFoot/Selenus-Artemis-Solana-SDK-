plugins { kotlin("multiplatform") }
kotlin {
    jvm()
    sourceSets {
        commonMain { dependencies {
            implementation(project(":artemis-core"))
            implementation(project(":artemis-rpc"))
            implementation(project(":artemis-tx"))
        }}
        commonTest { dependencies { implementation(kotlin("test")) }}
    }
}
