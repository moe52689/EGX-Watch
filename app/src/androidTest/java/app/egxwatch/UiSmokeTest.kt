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
    @org.junit.Before fun resetTestStorage() = kotlinx.coroutines.runBlocking {
        val app = rule.activity.application as WatchApplication
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.database.clearAllTables() }
        app.repository.initialize()
    }
    @Test fun mainNavigationAndInstrumentValidationAreVisible() {
        rule.onNodeWithText("EGX Watch").assertIsDisplayed()
        rule.waitUntil(10000) { runCatching { rule.onNodeWithText("0 instruments").assertIsDisplayed() }.isSuccess }
        rule.onNodeWithText("Discover").performClick()
        rule.onNodeWithText("Ticker or full name").performTextInput("CCAP")
        rule.onNodeWithText("Ticker or full name").performImeAction()
        rule.waitUntil(10000) { rule.onAllNodesWithText("1 results", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("discoverList").performScrollToNode(hasText("Validate & add"))
        rule.onNodeWithText("Qalaa for Financial Investments").assertIsDisplayed()
        rule.onNodeWithText("Validate & add").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithText("Added").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Added").assertIsNotEnabled()
        rule.onNodeWithText("Watchlist").performClick()
        rule.waitUntil(10000) { runCatching { rule.onNodeWithText("1 instruments").assertIsDisplayed() }.isSuccess }
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Your notification history").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Data connection").assertIsDisplayed()
        rule.onNodeWithText("Analytics, fallbacks & calendar").performClick()
        rule.onNodeWithText("Analytics & calendar").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Status").performClick()
        rule.onNodeWithText("Monitoring status").assertIsDisplayed()
    }
}
