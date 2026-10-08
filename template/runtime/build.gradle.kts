plugins { id("com.android.library") }

android {
    namespace = "com.dwl.mutcube.template.runtime"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:context"))
    api(project(":template:core"))
    testImplementation(project(":template:builtin"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:ai"))
    implementation(project(":core:security"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
