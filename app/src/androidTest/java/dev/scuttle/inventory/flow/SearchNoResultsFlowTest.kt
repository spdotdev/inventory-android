@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
class SearchNoResultsFlowTest : FlowTestBase() {
    @Test
    fun search_with_no_matches_shows_empty_state() {
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

            // Drawer → Search
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Search").and(hasClickAction()), timeoutMillis = 5_000)
            mockServer.route("/households", fixture("households_one.json"))
            onAllNodesWithText("Search").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // Type a query that returns no results
            waitUntilAtLeastOneExists(hasTestTag("search_field"), timeoutMillis = 5_000)
            mockServer.route("/households/1/search", fixture("search_empty.json"))
            onNodeWithTag("search_field").performTextInput("Xyzzy")
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("No matches."), timeoutMillis = 5_000)
            onNodeWithText("No matches.").assertIsDisplayed()
        }
    }
}
