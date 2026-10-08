plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.dwl.mutcube.core.database"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Export the latest Room schema before packaging migration-test assets.
tasks.configureEach {
    Regex("merge(.+)AndroidTestAssets").matchEntire(name)?.let { match ->
        dependsOn("ksp${match.groupValues[1]}Kotlin")
    }
}

dependencies {
    api(project(":template:core"))
    implementation(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // MutCubeDatabase is part of this module's public API and extends RoomDatabase.
    api("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
}
