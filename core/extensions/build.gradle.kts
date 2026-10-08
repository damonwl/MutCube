plugins {
    id("com.android.library")
}

android {
    namespace = "com.dwl.mutcube.core.extensions"
    compileSdk = 37

    defaultConfig { minSdk = 26 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.15.0")
    implementation("io.ktor:ktor-client-okhttp:3.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
}
