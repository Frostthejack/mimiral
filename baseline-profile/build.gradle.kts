plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.mimiral.app.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Target the app module — this module runs tests against :app
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Baseline profile generation configuration.
// The profile will be generated when running:
//   ./gradlew :app:generateBaselineProfile
// and saved to app/src/main/baseline-prof.txt
baselineProfile {
    // Use the GMD (Gradle Managed Device) for profile generation if available,
    // otherwise fall back to a connected device.
    // No filters block needed — the generator automatically profiles
    // the startup path covered by BaselineProfileGenerator.
}

dependencies {
    // Baseline profile generation uses the benchmark-macro-junit4 library
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.2")
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.2.0")
}
