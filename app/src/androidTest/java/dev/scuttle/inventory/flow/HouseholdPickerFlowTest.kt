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

/**
 * Replaces the deleted SearchHouseholdPickerFlowTest (Task 7's bottom-nav rework
 * removed the Search tab and its household-picker ModalBottomSheet with it, and the
 * test along with it — see `git log --diff-filter=D` on
 * app/src/androidTest/java/dev/scuttle/inventory/flow/SearchHouseholdPickerFlowTest.kt).
 *
 * Final review, Blocker 2: every replacement entry point (Dashboard's top-bar icon and
 * products stat card, the Storage tab's top-bar icon, Missing items' top-bar icon, the
 * bottom-bar Scan tab's LOOKUP mode) had regressed to hard-coding the FIRST household,
 * making a second household's search reachable only by drilling Home → that
 * household's own "+" icon → Storage overview → its search icon. The picker (now a
 * shared HouseholdPickerSheet, reused by all of those entry points) fixes that; this
 * test drives it from Dashboard's top-bar search icon — the one entry point reachable
 * without a real camera scan — and proves picking the SECOND household actually issues
 * a search request against THAT household's endpoint, not merely that some search
 * screen appears, by giving each household a distinct results fixture and asserting on
 * the household-2-specific content while asserting the household-1-specific content is
 * absent.
 */
@HiltAndroidTest
class HouseholdPickerFlowTest : FlowTestBase() {
    @Test
    fun picking_second_household_from_the_dashboard_picker_searches_that_households_endpoint() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_two.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        // Household 2 ("Office") has no locations — keeps the fixture set minimal while
        // still letting HierarchyStore's login-time refresh fully populate both
        // households (required for the picker to show at all).
        mockServer.route("/households/2/locations", fixture("locations_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // "across 2 households" only renders once DashboardUiState.households has
            // BOTH entries — HierarchyStore.refresh() publishes entries atomically
            // (buildFromNetwork resolves every household before the one _state.value
            // write), so this is the real signal that household 2 finished loading
            // too, not just household 1. Tapping search before this could silently
            // navigate straight through instead of opening the picker at all,
            // testing the wrong thing entirely.
            waitUntilAtLeastOneExists(hasText("across 2 households"), timeoutMillis = 8_000)

            mockServer.route("/households/1/search", fixture("search_results.json"))
            mockServer.route("/households/2/search", fixture("search_results_household2.json"))

            // Two households -> tapping Dashboard's search icon must open the
            // picker, not navigate directly.
            onNodeWithContentDescription("Search").performClick()
            waitUntilAtLeastOneExists(hasTestTag("household-picker-Office"), timeoutMillis = 5_000)
            onNodeWithTag("household-picker-Home").assertIsDisplayed()
            onNodeWithTag("household-picker-Office").assertIsDisplayed()

            // Pick the SECOND household ("Office", id 2) — not the first.
            onNodeWithTag("household-picker-Office").performClick()
            waitUntilAtLeastOneExists(hasTestTag("search_field"), timeoutMillis = 5_000)

            onNodeWithTag("search_field").performTextInput("Coffee")
            Thread.sleep(1_500)
            waitForIdle()

            // Household 2's result shows...
            waitUntilAtLeastOneExists(hasText("Pantry › Bottom shelf"), timeoutMillis = 5_000)
            onNodeWithText("Pantry › Bottom shelf").assertIsDisplayed()
            // ...and household 1's does not: proves the search request hit household 2's
            // endpoint, not household 1's (the exact bug this branch's final review caught).
            onNodeWithText("Fridge › Top shelf").assertDoesNotExist()
        }
    }
}
