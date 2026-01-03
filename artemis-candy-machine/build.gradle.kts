plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm()

  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(project(":artemis-runtime"))
        implementation(project(":artemis-tx"))
        implementation(project(":artemis-programs"))
        implementation(project(":artemis-discriminators"))
        implementation(project(":artemis-nft-compat"))
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
      }
    }
  }
}
