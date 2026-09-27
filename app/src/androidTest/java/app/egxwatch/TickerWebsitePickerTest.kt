package app.egxwatch

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.egxwatch.ui.TickerWebsitePicker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TickerWebsitePickerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun websiteChoicesOpenExactBrowserUrlsAndCustomDoesNotOpenBrowser() {
        val opened = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened.add(uri) }
            }) { MaterialTheme { TickerWebsitePicker() } }
        }
        rule.onNodeWithText("They do not supply in-app quotes or alerts.", substring = true).assertIsDisplayed()
        var selected = "Choose website or custom gateway"
        listOf(
            "EGX" to "https://www.egx.com.eg/en/prices.aspx",
            "Mubasher" to "https://english.mubasher.info/markets/EGX/",
            "TradingView" to "https://www.tradingview.com/markets/stocks-egypt/"
        ).forEach { (name, url) ->
            rule.onNodeWithText(selected).performClick()
            rule.onNodeWithText("$name · open website").performClick()
            rule.runOnIdle { assertEquals(url, opened.last()) }
            selected = name
        }
        rule.onNodeWithText(selected).performClick()
        rule.onNodeWithText("Custom authorized gateway").performClick()
        rule.onNodeWithText("Enter your authorized HTTPS API base URL below", substring = true).assertIsDisplayed()
        rule.runOnIdle { assertEquals(3, opened.size) }
    }

    @Test fun missingBrowserShowsRecoverableMessage() {
        rule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { throw IllegalArgumentException("No browser") }
            }) { MaterialTheme { TickerWebsitePicker() } }
        }
        rule.onNodeWithText("Choose website or custom gateway").performClick()
        rule.onNodeWithText("EGX · open website").performClick()
        rule.onNodeWithText("No browser available.", substring = true).assertIsDisplayed()
    }
}
