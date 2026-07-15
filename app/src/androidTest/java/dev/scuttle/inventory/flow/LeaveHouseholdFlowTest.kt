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
import androidx.compose.ui.test.performScrollTo
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
        // households_two.json also carries household 2 ("Office") — HierarchyStore's
        // full walk fetches ITS locations too. MockWebServerRule dispatches by path
        // PREFIX only (no method/exact-path awareness), and "/households/2/locations"
        // is itself a prefix match for the plain "/households" route below, so without
        // a dedicated route here that background GET steals an entry meant for one of
        // this test's own explicit "/households" registrations (My households screen /
        // leave()'s own re-list) — see HouseholdPickerFlowTest / DashboardHouseholdAttributionFlowTest
        // for the same pattern with the same fixture pair.
        mockServer.route("/households/2/locations", fixture("locations_empty.json"))

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
            // mode: tap the pencil, then the "Home" row. clickNameArea(), not
            // performClick(): this row is the same EditableRow-shaped card (leading
            // avatar/name, trailing "Share"/invite icon button) whose node-CENTER
            // tap was confirmed (via a printToLog dump of the semantics tree on real
            // CI hardware, chasing this same-shaped failure on the shelf/location
            // rows — see FlowTestBase.clickNameArea's doc) to land on trailing
            // icon-button territory instead of the card's own onClick on some screen
            // sizes.
            onNodeWithContentDescription("Edit households").performClick()
            waitForIdle()
            onNodeWithContentDescription("Home").clickNameArea()
            // Every other cross-screen navigation in this suite pairs its click with a
            // real settle delay (not just waitForIdle()) before asserting on the
            // destination — this was the one navigation step in this file that didn't,
            // even though it's a full NavHost route change (Households -> household-edit/1),
            // same class of transition CreateLocationFlowTest/DeleteLocationFlowTest etc.
            // give 2s to settle.
            Thread.sleep(2_000)
            waitForIdle()

            // The edit screen's danger zone.
            waitUntilAtLeastOneExists(hasText("Danger zone"), timeoutMillis = 5_000)

            // Register a specific route for the DELETE so it doesn't consume the /households GET response
            mockServer.route("/households/1/leave", "", code = 204)
            mockServer.route("/households", fixture("households_office_only.json"))

            // Two "Leave" nodes on this screen: [0] = danger-zone button, [1] = the
            // confirm dialog's button once it opens. HouseholdEditScreen's content
            // Column is verticalScroll()-able, and the danger zone sits at the very
            // bottom of it — confirmed via a printToLog dump on real CI hardware
            // (root count stayed 1, i.e. no dialog ever opened) that on CI's shorter
            // screen the "Leave" button's own layout bounds sit entirely BELOW the
            // visible viewport (root 0-640px tall; the button at y=696-736px).
            // performClick()'s synthetic touch targets the node's on-screen
            // coordinate, which doesn't exist off-viewport — a silent no-op, same as
            // MandatoryToggleFlowTest already works around for its own scrolled
            // content via performScrollTo().
            onAllNodesWithText("Leave")[0].performScrollTo()
            onAllNodesWithText("Leave")[0].performClick()
            waitUntilAtLeastOneExists(hasText("Leave Home?"), timeoutMillis = 8_000)
            onAllNodesWithText("Leave")[1].performClick()

            Thread.sleep(2_000)
            waitForIdle()

            // leave() only navigates back once it actually completes server-side
            // (HouseholdsUiState.leftHouseholdId) — back on the households list,
            // "Home" is gone and "Office" is the only household left.
            onNodeWithText("Leave Home?").assertDoesNotExist()
            onAllNodesWithText("Office")[0].assertIsDisplayed()
        }
    }
}
