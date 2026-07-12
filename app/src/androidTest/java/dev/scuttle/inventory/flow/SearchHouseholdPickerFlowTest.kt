@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
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

/**
 * The multi-household search picker (MainActivity's ModalBottomSheet, shown when the
 * Search tab is tapped and drawerUi.entries.size > 1) had zero instrumented coverage.
 * An earlier review caught a regression where the picker silently always navigated to
 * the FIRST household regardless of which row was tapped. This test proves the picker
 * opens with two households, and that picking the SECOND one actually issues a search
 * request against THAT household's endpoint — not merely that some search screen
 * appears — by giving each household a distinct results fixture and asserting on the
 * household-2-specific content while asserting the household-1-specific content is
 * absent.
 */
@HiltAndroidTest
class SearchHouseholdPickerFlowTest : FlowTestBase() {
    @Test
    fun picking_second_household_searches_that_households_endpoint() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_two.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        // Household 2 ("Office") has no locations — keeps the fixture set minimal while
        // still letting HierarchyStore's login-time refresh fully populate both
        // households in drawerUi.entries (required for the picker to show at all).
        mockServer.route("/households/2/locations", fixture("locations_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // Distinct fixtures per household: a regression that always searches
            // household 1 would surface household 1's content here, not "no content" —
            // a strictly stronger signal than just checking a search screen appeared.
            mockServer.route("/households/1/search", fixture("search_results.json"))
            mockServer.route("/households/2/search", fixture("search_results_household2.json"))

            // The Search tab is disabled until drawerUi.entries loads (HierarchyStore,
            // async after login), and — crucially for THIS test — merely waiting for the
            // tab to become *enabled* only proves entries is non-empty, i.e. at least ONE
            // household loaded. That's not enough here: if only household 1 had loaded,
            // entries.size == 1 and tapping Search would navigate straight into its
            // search screen instead of opening the picker, silently testing the wrong
            // thing. So prove BOTH households are present first: visit the Households tab
            // (unconditionally enabled, unrelated to this gating) and wait for the second
            // household ("Office") to render there — that only happens once the
            // households list itself has loaded seconds after login. Re-queue /households
            // since HouseholdsViewModel does its own independent fetch on init/visit.
            mockServer.route("/households", fixture("households_two.json"))
            onNodeWithTag("bottom-nav-households").performClick()
            waitUntilAtLeastOneExists(hasContentDescription("Office"), timeoutMillis = 8_000)

            // Now safe to assume both households are in drawerUi.entries too (same login
            // load produces both) — wait for Search to be enabled, then tap it.
            waitUntilAtLeastOneExists(hasTestTag("bottom-nav-search").and(isEnabled()), timeoutMillis = 8_000)

            // Two households -> tapping Search must open the picker, not navigate directly.
            onNodeWithTag("bottom-nav-search").performClick()
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
            // endpoint, not household 1's (the exact bug an earlier review caught).
            onNodeWithText("Fridge › Top shelf").assertDoesNotExist()
        }
    }
}
