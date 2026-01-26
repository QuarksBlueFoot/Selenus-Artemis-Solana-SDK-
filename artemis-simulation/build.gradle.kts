plugins {
    id("artemis.kotlin-library-conventions")
}

dependencies {
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-tx"))
    implementation(project(":artemis-rpc"))
    implementation(project(":artemis-compute"))
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
