package com.mimiral.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the Mimiral app's critical startup path.
 *
 * Baseline Profiles specify classes and methods that the Android Runtime (ART)
 * should pre-verify and pre-compile at install time, eliminating the runtime
 * DEX verification cost that causes ANRs on cold start for large apps.
 *
 * To generate the profile, run on a rooted device or emulator:
 *   ./gradlew :app:generateBaselineProfile
 *
 * The generated profile (baseline-prof.txt) is automatically included in
 * release APKs and ART pre-compiles the listed code paths at install time.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.mimiral.app",
            profileBlock = {
                // Cold start — the profile captures all methods executed
                // from process init through Activity.onCreate, Compose layout,
                // and Hilt DI reaching idle state.
                startActivityAndWait()
            }
        )
    }
}
