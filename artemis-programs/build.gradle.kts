plugins{ kotlin("jvm") }
dependencies {
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-tx"))
}
