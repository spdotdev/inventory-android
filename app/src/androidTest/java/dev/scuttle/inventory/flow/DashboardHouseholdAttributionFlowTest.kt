@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * Issue #33: with two households the dashboard aggregated everything into one flat
 * list, so a location gave no clue which household it lived in. It must now name the
 * household each location belongs to — and stay quiet when there's only one.
 */
@HiltAndroidTest
class DashboardHouseholdAttributionFlowTest : FlowTestBase() {
    private fun signIn() {
        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)
        }
    }

    @Test
    fun two_households_each_head_their_own_locations() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_two.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_empty.json"))
        mockServer.route("/households/2/locations", fixture("locations_office.json"))
        mockServer.route("/households/2/locations/20/shelves", fixture("shelves_empty.json"))

        signIn()

        composeRule.apply {
            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 8_000)

            // Both households are named, and each one's location is on screen. Without
            // the headers "Fridge" and "Kelder" were indistinguishable neighbours.
            onNodeWithText("Home").assertIsDisplayed()
            onNodeWithText("Office").assertIsDisplayed()
            onNodeWithText("Fridge").assertIsDisplayed()
            onNodeWithText("Kelder").assertIsDisplayed()

            // The stat cards sum both households, and now say so.
            onNodeWithText("across 2 households").assertIsDisplayed()
        }
    }

    @Test
    fun a_single_household_is_not_labelled() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_empty.json"))

        signIn()

        composeRule.apply {
            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 8_000)

            // Nothing to disambiguate: no household header, no "across N" caption.
            onNodeWithText("Home").assertDoesNotExist()
            onNodeWithText("across 1 households").assertDoesNotExist()
        }
    }
}
