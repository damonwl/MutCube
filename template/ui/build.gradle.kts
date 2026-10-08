plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.dwl.mutcube.template.ui"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(project(":template:runtime"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.material:material-icons-extended")
}
