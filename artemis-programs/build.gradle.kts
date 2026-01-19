plugins{ kotlin("jvm") }
dependencies {
    testImplementation(kotlin("test"))
    implementation(project(":artemis-core"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-tx"))
}
