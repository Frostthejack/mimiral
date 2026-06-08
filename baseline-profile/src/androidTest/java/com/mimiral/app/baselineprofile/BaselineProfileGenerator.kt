package com.mimiral.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
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
 * This generator exercises the full startup path PLUS navigates through all
 * major screens via the navigation drawer. This ensures ART captures the
 * app's own classes (com.mimiral.app.*) in the profile, not just the
 * framework/library classes that startActivityAndWait() alone would cover.
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
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        baselineProfileRule.collect(
            packageName = "com.mimiral.app",
            profileBlock = {
                // Phase 1: Cold start — captures process init, Activity.onCreate,
                // Compose layout, Hilt DI, and the Library screen rendering.
                startActivityAndWait()

                // Wait for the Library screen to fully render
                device.wait(Until.hasObject(By.text("Library")), 10_000)

                // Phase 2: Navigate through all major screens via the drawer.
                // Each navigation triggers lazy initialization of ViewModels,
                // Compose layouts, database DAOs, and format parsers — ensuring
                // these classes are verified and captured in the baseline profile.

                // Navigate to Collections
                navigateToDrawerItem(device, "Collections")
                waitForScreen(device, "Collections", 5_000)

                // Navigate to Lists (Reading Lists)
                navigateToDrawerItem(device, "Lists")
                waitForScreen(device, "Lists", 5_000)

                // Navigate to Discover
                navigateToDrawerItem(device, "Discover")
                waitForScreen(device, "Discover", 5_000)

                // Navigate to Now Reading
                navigateToDrawerItem(device, "Now Reading")
                waitForScreen(device, "Now Reading", 5_000)

                // Navigate to Statistics
                navigateToDrawerItem(device, "Statistics")
                waitForScreen(device, "Statistics", 5_000)

                // Navigate to Goals
                navigateToDrawerItem(device, "Goals")
                waitForScreen(device, "Goals", 5_000)

                // Navigate to Settings
                navigateToDrawerItem(device, "Settings")
                waitForScreen(device, "Settings", 5_000)

                // Navigate back to Library (start destination)
                navigateToDrawerItem(device, "Library")
                waitForScreen(device, "Library", 5_000)

                // Phase 3: Exercise the Add Books screen (triggers FileScanner,
                // MediaStore queries, SAF URI handling)
                val addBooksButton = device.findObject(By.desc("Add books"))
                if (addBooksButton != null) {
                    addBooksButton.click()
                    device.waitForIdle(3_000)
                    // Press back to return to Library
                    device.pressBack()
                    device.waitForIdle(2_000)
                }
            }
        )
    }

    /**
     * Opens the navigation drawer and clicks on the item with the given label.
     * Uses the hamburger menu (contentDescription "Open navigation menu") to open
     * the drawer, then finds the item by text.
     */
    private fun navigateToDrawerItem(device: UiDevice, label: String) {
        // Open the drawer via the hamburger button
        val menuButton = device.findObject(By.desc("Open navigation menu"))
        if (menuButton != null) {
            menuButton.click()
            device.waitForIdle(1_500)
        }

        // Click the drawer item by its label text
        val drawerItem = device.findObject(By.text(label))
        if (drawerItem != null) {
            drawerItem.click()
            device.waitForIdle(2_000)
        }
    }

    /**
     * Waits up to [timeoutMs] for an object with the given text to appear,
     * confirming the screen has loaded.
     */
    private fun waitForScreen(device: UiDevice, text: String, timeoutMs: Long) {
        device.wait(Until.hasObject(By.text(text)), timeoutMs)
        device.waitForIdle(1_000)
    }
}
