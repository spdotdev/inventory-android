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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * Edit mode's rename affordance (Task 5) had zero flow-level coverage before
 * this: the per-row pencil on StorageOverviewScreen navigates to the
 * standalone full-page StorageEditScreen (title/content-description both
 * "Edit storage location" — R.string.storage_edit_title, passed both as the
 * page's TopAppBar title and as EditableRow's renameLabelRes for the pencil
 * itself) that PATCHes the location. Re-routed 2026-07-27 (storage theming)
 * from the screen's old inline rename bottom sheet onto this page, mirroring
 * RenameShelfFlowTest-equivalent coverage moving onto ShelfEditScreen earlier.
 * This isn't one of the delete-safety guarantees this task's other tests
 * pin — it's the companion regression net for the other edit-mode action on
 * the same screen, over the same real UI and wire.
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

            // Gear ("Manage storage") — with exactly one household this navigates
            // straight to its Storage overview screen.
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Manage storage").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Fridge"), timeoutMillis = 5_000)

            // Edit mode → per-row rename pencil (content description "Edit storage
            // location" — distinct from the top bar's "Edit storage" that enters
            // edit mode in the first place). It now NAVIGATES to the standalone
            // StorageEditScreen (2026-07-27, storage theming) rather than opening
            // an inline bottom sheet.
            onNodeWithContentDescription("Edit storage").performClick()
            waitForIdle()
            onNodeWithContentDescription("Edit storage location").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasTestTag("location-name-field"), timeoutMillis = 5_000)

            // "Fridge" is ambiguous on this page — it's both the prefilled name
            // field's value AND the "Fridge" type FilterChip's label. Select the
            // name field by its own stable test tag rather than by text.
            onNodeWithTag("location-name-field").performTextClearance()
            onNodeWithTag("location-name-field").performTextInput("Walk-in Fridge")

            mockServer.route("/households/1/locations/10", fixture("location_renamed.json"))
            onNodeWithTag("location-save-name").performClick()
            waitForIdle()
            Thread.sleep(1_000)
            waitForIdle()

            onNodeWithContentDescription("Back").performClick()
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
