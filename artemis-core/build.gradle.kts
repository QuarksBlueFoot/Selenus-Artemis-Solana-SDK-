plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib"))
        }
    }
}
