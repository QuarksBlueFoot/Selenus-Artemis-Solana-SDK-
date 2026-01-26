plugins {
    id("artemis.kotlin-library-conventions")
}

dependencies {
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-ws"))
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
