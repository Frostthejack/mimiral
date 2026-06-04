package com.mimiral.app

import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Base instrumented test class for Mimiral.
 *
 * Verifies that:
 *  - The app launches without crashing
 *  - No FATAL exceptions or ANRs appear in logcat during launch
 *
 * Subclass this for specific instrumented tests.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
open class MimiralInstrumentedTest {

    /**
     * Launches MainActivity before each test and closes it after.
     */
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    protected lateinit var appContext: android.content.Context

    @Before
    open fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear logcat before each test to have a clean baseline
        Runtime.getRuntime().exec("logcat -c")
    }

    @After
    open fun tearDown() {
        // After each test, check logcat for fatal/anr errors
        verifyNoFatalErrors()
    }

    /**
     * Base test: verify the app launches and the context is correct.
     */
    @Test
    open fun appLaunchesWithoutCrash() {
        val packageName = appContext.packageName
        assertEquals(
            "Expected package com.mimiral.app but was $packageName",
            "com.mimiral.app",
            packageName
        )
        Log.d(TAG, "App launched successfully – package=$packageName")
    }

    /**
     * Scans logcat for FATAL_EXCEPTION or ANR entries since setUp().
     * Fails the test if any are found.
     */
    protected fun verifyNoFatalErrors() {
        try {
            val process = Runtime.getRuntime().exec("logcat -d *:E")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errors = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.contains("FATAL_EXCEPTION", ignoreCase = true) ||
                    trimmed.contains("ANR", ignoreCase = true) ||
                    trimmed.contains("AndroidRuntime", ignoreCase = true)
                ) {
                    errors.add(trimmed)
                }
            }
            reader.close()

            if (errors.isNotEmpty()) {
                val summary = errors.take(5).joinToString("\n")
                val more = if (errors.size > 5) "\n... and ${errors.size - 5} more" else ""
                fail("Found ${errors.size} critical error(s) in logcat:\n$summary$more")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read logcat during tearDown: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MimiralInstrumentedTest"
    }
}
