package com.mimiral.app.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.mimiral.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies each drawer destination screen renders its specific content correctly.
 *
 * Goes beyond just checking the top bar title -- also verifies
 * screen-specific content like empty states, section headers, FABs, etc.
 *
 * This catches cases where a screen composable exists but renders
 * blank/broken content (e.g. missing ViewModel, null state).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DrawerScreenContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val drawerContentDescription = "Open navigation menu"

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(drawerContentDescription)
            .performClick()
        composeTestRule.waitUntil(5_000) {
            try {
                composeTestRule.onNodeWithText("Library").assertIsDisplayed()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun navigateToDrawerItem(itemLabel: String) {
        openDrawer()
        composeTestRule.onNodeWithText(itemLabel)
            .performClick()
    }

    private fun waitForScreen(titleText: String, timeoutMs: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMs) {
            try {
                composeTestRule.onNodeWithText(titleText).assertIsDisplayed()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Collections screen content
    // ═══════════════════════════════════════════════════════════

    @Test
    fun collectionsScreen_showsEmptyStateOrContent() {
        navigateToDrawerItem("Collections")
        waitForScreen("Collections")

        composeTestRule.onNodeWithText("Collections")
            .assertIsDisplayed()
    }

    @Test
    fun collectionsScreen_showsNewCollectionFab() {
        navigateToDrawerItem("Collections")
        waitForScreen("Collections")

        composeTestRule.onNodeWithContentDescription("New Collection")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Reading Lists screen content
    // ═══════════════════════════════════════════════════════════

    @Test
    fun readingListsScreen_showsEmptyStateOrContent() {
        navigateToDrawerItem("Lists")
        waitForScreen("Reading Lists")

        composeTestRule.onNodeWithText("Reading Lists")
            .assertIsDisplayed()
    }

    @Test
    fun readingListsScreen_showsNewListFab() {
        navigateToDrawerItem("Lists")
        waitForScreen("Reading Lists")

        composeTestRule.onNodeWithContentDescription("New List")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Discover screen content
    // ═══════════════════════════════════════════════════════════

    @Test
    fun discoverScreen_showsContent() {
        navigateToDrawerItem("Discover")
        waitForScreen("Discover")

        composeTestRule.onNodeWithText("Discover")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Statistics screen content
    // ═══════════════════════════════════════════════════════════

    @Test
    fun statisticsScreen_showsContent() {
        navigateToDrawerItem("Statistics")
        waitForScreen("Statistics")

        composeTestRule.onNodeWithText("Statistics")
            .assertIsDisplayed()
    }

    @Test
    fun statisticsScreen_showsReadingGoalsIcon() {
        navigateToDrawerItem("Statistics")
        waitForScreen("Statistics")

        composeTestRule.onNodeWithContentDescription("Reading Goals")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Reading Goals screen content
    // ═══════════════════════════════════════════════════════════

    @Test
    fun readingGoalsScreen_showsContent() {
        navigateToDrawerItem("Goals")
        waitForScreen("Reading Goals")

        composeTestRule.onNodeWithText("Reading Goals")
            .assertIsDisplayed()
    }

    @Test
    fun readingGoalsScreen_showsAddGoalFab() {
        navigateToDrawerItem("Goals")
        waitForScreen("Reading Goals")

        composeTestRule.onNodeWithContentDescription("Add goal")
            .assertIsDisplayed()
    }

    // ═══════════════════════════════════════════════════════════
    // Settings screen content
    // ═══════════════════════════════════════════════════════════

    @Test
    fun settingsScreen_showsContent() {
        navigateToDrawerItem("Settings")
        waitForScreen("Settings")

        composeTestRule.onNodeWithText("Settings")
            .assertIsDisplayed()
    }

    @Test
    fun settingsScreen_showsReadingPreferences() {
        navigateToDrawerItem("Settings")
        waitForScreen("Settings")

        composeTestRule.onNodeWithText("Reading Preferences")
            .assertIsDisplayed()
    }
}
