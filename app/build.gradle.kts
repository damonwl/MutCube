plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin.android")
}

android {
    namespace = "com.dwl.mutcube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dwl.mutcube"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.dwl.mutcube"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        create("alpha") {
            initWith(getByName("release"))
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appAuthRedirectScheme"] = "com.dwl.mutcube.alpha"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:context"))
    implementation(project(":core:model"))
    implementation(project(":core:ai"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(project(":core:extensions"))
    implementation(project(":feature:chat"))
    implementation(project(":template:runtime"))
    implementation(project(":template:ui"))
    implementation(project(":template:builtin"))
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.composables:composeunstyled-modal-bottom-sheet:2.9.2")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("io.github.boswelja.markdown:material3:1.3.0")
    implementation("org.jetbrains:markdown:0.7.5") // Reuse the renderer's GFM parser for table blocks.
    implementation("net.openid:appauth:0.11.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.yaml:snakeyaml:2.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
}
