plugins{ kotlin("jvm") }
dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-tx"))
}
