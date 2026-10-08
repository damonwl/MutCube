plugins { id("com.android.library") }
android {
    namespace = "com.dwl.mutcube.template.builtin"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api(project(":template:core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    val exportPath = providers.gradleProperty("fitnessExportPath")
    inputs.property("fitnessExportPath", exportPath.orNull ?: "")
    if (exportPath.isPresent) systemProperty("fitnessExportPath", exportPath.get())
}
