package com.solidlink.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class HomeScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenShowsRealTransferAndPrivacyControls() {
        composeRule.onNodeWithText("SolidLink").assertIsDisplayed()
        composeRule.onNodeWithText("Choose files").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy controls").assertIsDisplayed()
        composeRule.onNodeWithText("Local-only routing").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing transferred yet").assertIsDisplayed()
    }
}
