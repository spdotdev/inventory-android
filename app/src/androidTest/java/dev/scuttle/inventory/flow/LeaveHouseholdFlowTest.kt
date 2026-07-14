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
class LeaveHouseholdFlowTest : FlowTestBase() {
    @Test
    fun leave_household_removes_it_from_list() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_two.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // Households leaves the bottom bar in favour of Settings ("More") →
            // "My households".
            mockServer.route("/households", fixture("households_two.json"))
            onNodeWithTag("bottom-nav-more").performClick()
            waitForIdle()
            onNodeWithText("My households").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Office"), timeoutMillis = 5_000)

            // Leave now lives in the household's own edit screen, reached via edit
            // mode: tap the pencil, then the "Home" row.
            onNodeWithContentDescription("Edit households").performClick()
            waitForIdle()
            onNodeWithContentDescription("Home").performClick()
            waitForIdle()

            // The edit screen's danger zone.
            waitUntilAtLeastOneExists(hasText("Danger zone"), timeoutMillis = 5_000)

            // Register a specific route for the DELETE so it doesn't consume the /households GET response
            mockServer.route("/households/1/leave", "", code = 204)
            mockServer.route("/households", fixture("households_office_only.json"))

            // Two "Leave" nodes on this screen: [0] = danger-zone button, [1] = the
            // confirm dialog's button once it opens.
            onAllNodesWithText("Leave")[0].performClick()
            waitUntilAtLeastOneExists(hasText("Leave Home?"), timeoutMillis = 3_000)
            onAllNodesWithText("Leave")[1].performClick()

            Thread.sleep(2_000)
            waitForIdle()

            // leave() only navigates back once it actually completes server-side
            // (HouseholdsUiState.left) — back on the households list, "Home" is
            // gone and "Office" is the only household left.
            onNodeWithText("Leave Home?").assertDoesNotExist()
            onAllNodesWithText("Office")[0].assertIsDisplayed()
        }
    }
}
