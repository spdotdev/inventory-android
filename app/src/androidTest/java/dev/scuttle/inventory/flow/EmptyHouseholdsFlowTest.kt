@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import org.junit.Test

@HiltAndroidTest
class EmptyHouseholdsFlowTest : FlowTestBase() {

    @Test
    fun households_screen_empty_state_shown_when_no_households() {
        // Login succeeds but household list is empty (new account with no households yet)
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_empty.json"))
        mockServer.route("/households", fixture("households_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)

            // Drawer → Households; HouseholdsViewModel.refresh() → GET /households → empty
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Households").and(hasClickAction()), timeoutMillis = 5_000)

            mockServer.route("/households", fixture("households_empty.json"))
            onAllNodesWithText("Households").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("No households yet", substring = true), timeoutMillis = 5_000)
            onNodeWithText("No households yet. Tap + to create one.").assertIsDisplayed()
        }
    }
}
