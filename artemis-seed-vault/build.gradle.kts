plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.selenus.artemis.seedvault"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":artemis-core"))
    implementation(project(":artemis-runtime"))
    implementation(project(":artemis-wallet"))
    implementation(project(":artemis-tx"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
