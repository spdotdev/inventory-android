@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * Edit mode's rename affordance (Task 5) had zero flow-level coverage before
 * this: the per-row pencil on StorageOverviewScreen opens a bottom sheet
 * (title/content-description both "Edit storage location" —
 * R.string.storage_edit_title, passed as EditableRow's renameLabelRes) that
 * PATCHes the location. This isn't one of the delete-safety guarantees this
 * task's other tests pin — it's the companion regression net for the other
 * edit-mode action on the same screen, over the same real UI and wire.
 */
@HiltAndroidTest
class RenameLocationFlowTest : FlowTestBase() {
    @Test
    fun renaming_a_location_updates_the_list_and_sends_the_new_name_on_the_wire() {
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

            // Storage tab → AllStoragesScreen
            onNodeWithTag("bottom-nav-home").performClick()
            waitForIdle()
            Thread.sleep(2_000)
            waitForIdle()

            // "Add storage location" (the + icon next to "Home") → StorageOverviewScreen
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Add storage location").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 5_000)

            // Edit mode → per-row rename pencil (content description "Edit storage
            // location" — distinct from the top bar's "Edit storage" that enters
            // edit mode in the first place).
            onNodeWithContentDescription("Edit storage").performClick()
            waitForIdle()
            onNodeWithContentDescription("Edit storage location").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Edit storage location"), timeoutMillis = 3_000)

            // "Fridge" is ambiguous on this sheet — it's both the prefilled name
            // field's value AND the "Fridge" type FilterChip's label. The name
            // field is the sheet's only set-text-actionable node, so select by
            // that action rather than by text (which the clear below would
            // invalidate on a second, matcher-re-evaluated lookup). Mirrors
            // EditProductDetailFlowTest's own re-query-per-action idiom.
            waitUntilAtLeastOneExists(hasSetTextAction(), timeoutMillis = 3_000)
            onAllNodes(hasSetTextAction())[0].performTextClearance()
            onAllNodes(hasSetTextAction())[0].performTextInput("Walk-in Fridge")

            mockServer.route("/households/1/locations/10", fixture("location_renamed.json"))
            onNodeWithText("Save").performClick()
            waitForIdle()
            Thread.sleep(1_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Walk-in Fridge"), timeoutMillis = 5_000)
            onNodeWithText("Walk-in Fridge").assertIsDisplayed()
            onNodeWithText("Fridge").assertDoesNotExist()
        }

        val patchRequest =
            generateSequence { mockServer.server.takeRequest(1, java.util.concurrent.TimeUnit.SECONDS) }
                .firstOrNull { it.path == "/households/1/locations/10" }
        requireNotNull(patchRequest) { "App never called PATCH /households/1/locations/10" }
        assert(patchRequest.method == "PATCH") { "expected PATCH, got ${patchRequest.method}" }
        val body = patchRequest.body.readUtf8()
        assert(body.contains("\"name\":\"Walk-in Fridge\"")) { "new name missing from body: $body" }
    }
}
