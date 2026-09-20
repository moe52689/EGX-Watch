package app.egxwatch

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun mainNavigationAndInstrumentValidationAreVisible() {
        rule.onNodeWithText("EGX Watch").assertIsDisplayed()
        rule.onNodeWithText("Discover").performClick()
        rule.onNodeWithText("Ticker or full name").performTextInput("CCAP")
        rule.waitUntil(5000) { rule.onAllNodesWithText("Validate & add").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Qalaa for Financial Investments").assertIsDisplayed()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Your notification history").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Data connection").assertIsDisplayed()
        rule.onNodeWithText("Use free public feeds").assertIsDisplayed()
    }
}
