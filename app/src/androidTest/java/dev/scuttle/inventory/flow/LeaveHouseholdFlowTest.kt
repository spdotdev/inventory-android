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
class LeaveHouseholdFlowTest : FlowTestBase() {

    @Test
    fun leave_household_removes_it_from_list() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_two.json"))
        mockServer.route("/households", fixture("households_two.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)

            // Open drawer → Households
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Households").and(hasClickAction()), timeoutMillis = 5_000)
            mockServer.route("/households", fixture("households_two.json"))
            onAllNodesWithText("Households").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Office"), timeoutMillis = 5_000)

            // Tap Leave on "Home" household (first in list) → confirm dialog
            // Register a specific route for the DELETE so it doesn't consume the /households GET response
            mockServer.route("/households/1/leave", "", code = 204)
            mockServer.route("/households", fixture("households_office_only.json"))
            onAllNodesWithText("Leave")[0].performClick()
            waitForIdle()
            // Dialog "Leave Home?" → 3 Leave nodes: [0]=Home btn, [1]=Office btn, [2]=dialog confirm
            waitUntilAtLeastOneExists(hasText("Leave Home?"), timeoutMillis = 3_000)
            onAllNodesWithText("Leave")[2].performClick()

            Thread.sleep(2_000)
            waitForIdle()
            // After leaving Home, the list refreshed. Office is the only remaining household.
            // "Leave Home?" dialog is gone and Office's Leave button is the only one shown.
            onNodeWithText("Leave Home?").assertDoesNotExist()
            onAllNodesWithText("Office")[0].assertIsDisplayed()
        }
    }
}
