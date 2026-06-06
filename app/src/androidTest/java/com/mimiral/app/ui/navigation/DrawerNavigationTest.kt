package com.mimiral.app.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mimiral.app.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies all navigation drawer screens render correctly.
 *
 * Each test opens the navigation drawer, taps a drawer item,
 * and asserts that the target screen's top bar title is displayed.
 *
 * Tests cover the 6 previously-unverified screens:
 * - Collections
 * - Lists (Reading Lists)
 * - Discover
 * - Statistics
 * - Goals (Reading Goals)
 * - Settings
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DrawerNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val drawerContentDescription = "Open navigation menu"

    @Before
    fun openDrawer() {
        // Open the navigation drawer by clicking the hamburger icon in the top bar
        composeTestRule.onNodeWithContentDescription(drawerContentDescription)
            .performClick()
        // Wait for the drawer to settle
        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Library").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Collections
    // ═══════════════════════════════════════════════════════════

    @Test
    fun collectionsScreen_rendersFromDrawer() {
        composeTestRule.onNodeWithText("Collections")
            .performClick()

        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Collections").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }

        composeTestRule.onNodeWithText("Collections")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Lists (Reading Lists)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun readingListsScreen_rendersFromDrawer() {
        composeTestRule.onNodeWithText("Lists")
            .performClick()

        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Reading Lists").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }

        composeTestRule.onNodeWithText("Reading Lists")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Discover
    // ═══════════════════════════════════════════════════════════

    @Test
    fun discoverScreen_rendersFromDrawer() {
        composeTestRule.onNodeWithText("Discover")
            .performClick()

        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Discover").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }

        composeTestRule.onNodeWithText("Discover")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Statistics
    // ═══════════════════════════════════════════════════════════

    @Test
    fun statisticsScreen_rendersFromDrawer() {
        composeTestRule.onNodeWithText("Statistics")
            .performClick()

        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Statistics").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }

        composeTestRule.onNodeWithText("Statistics")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Goals (Reading Goals)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun readingGoalsScreen_rendersFromDrawer() {
        composeTestRule.onNodeWithText("Goals")
            .performClick()

        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Reading Goals").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }

        composeTestRule.onNodeWithText("Reading Goals")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Settings
    // ═══════════════════════════════════════════════════════════

    @Test
    fun settingsScreen_rendersFromDrawer() {
        composeTestRule.onNodeWithText("Settings")
            .performClick()

        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: Exception) {
                false
            }
        }

        composeTestRule.onNodeWithText("Settings")
            .assertIsDisplayed()
    }
}
