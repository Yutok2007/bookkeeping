package com.pocketledger.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class MainActivitySmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun bottomNavigationAndAddTransactionOpen() {
        composeRule.onNodeWithText("+").fetchSemanticsNode().also { composeRule.onNodeWithText("+").performClick() }
        assertTrue(composeRule.onAllNodesWithText("+").fetchSemanticsNodes().isEmpty())
    }
}
