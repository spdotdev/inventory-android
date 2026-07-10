@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class RegisterFlowTest : FlowTestBase() {
    @Test
    fun register_new_account_navigates_to_dashboard() {
        // POST /auth/register returns same token shape as login
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            // Switch to register mode
            onNodeWithText("New here? Create an account").performClick()
            waitForIdle()

            onNodeWithText("Name").performTextInput("Test User")
            onNodeWithText("Email").performTextInput("new@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Create account").filterToOne(hasClickAction()).performClick()

            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 8_000)
            onNodeWithTag(DASHBOARD_TITLE_TEST_TAG).assertIsDisplayed()
        }
    }
}
