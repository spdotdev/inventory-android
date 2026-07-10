@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class JoinHouseholdFlowTest : FlowTestBase() {
    @Test
    fun join_with_code_shows_success() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // Open drawer → Settings
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-nav-settings"), timeoutMillis = 5_000)
            onNodeWithTag("drawer-nav-settings").performClick()
            waitForIdle()

            // Enter join code and submit — POST /households/join
            mockServer.enqueue(fixture("household_created.json"))
            onNodeWithText("Join code").performTextInput("BBBB-2222")
            onNodeWithText("Join").performClick()

            Thread.sleep(2_000)
            waitUntilAtLeastOneExists(hasText("Joined successfully!"), timeoutMillis = 5_000)
            onNodeWithText("Joined successfully!").assertIsDisplayed()
        }
    }
}
