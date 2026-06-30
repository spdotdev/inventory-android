@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasContentDescription
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
class CreateLocationFlowTest : FlowTestBase() {

    @Test
    fun add_storage_location_appears_in_list() {
        // Login: Dashboard + Drawer both call full hierarchy (×2 each)
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
            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)

            // Open drawer → "All storage" (AllStoragesScreen); this triggers DrawerViewModel.refresh()
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("All storage").and(hasClickAction()), timeoutMillis = 5_000)

            onAllNodesWithText("All storage").filterToOne(hasClickAction()).performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Tap "Add storage location" (the + icon next to "Home" household) → StorageOverviewScreen
            // StorageOverviewViewModel.load() calls GET /households/1/locations
            mockServer.route("/households/1/locations", fixture("locations_one.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Add storage location").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Now on StorageOverviewScreen — tap the FAB "Add storage location" to open the sheet
            // POST /households/1/locations → location_created.json (single dto, appended to list by VM)
            mockServer.route("/households/1/locations", fixture("location_created.json"))
            onNodeWithContentDescription("Add storage location").performClick()
            waitUntilAtLeastOneExists(hasText("Add storage"), timeoutMillis = 3_000)

            onNodeWithText("Name").performTextInput("Pantry")
            onNodeWithText("Add").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasContentDescription("Open Pantry"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Open Pantry").assertIsDisplayed()
        }
    }
}
