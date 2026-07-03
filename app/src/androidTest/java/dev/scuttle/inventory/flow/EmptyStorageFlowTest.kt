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
class EmptyStorageFlowTest : FlowTestBase() {

    @Test
    fun storage_overview_shows_empty_state_when_no_locations() {
        // Household exists but has no locations
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)

            // Drawer → All storage → tap "Add storage location" for Home → StorageOverviewScreen
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("All storage").and(hasClickAction()), timeoutMillis = 5_000)
            onAllNodesWithText("All storage").filterToOne(hasClickAction()).performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Tap the + button next to "Home" household
            mockServer.route("/households/1/locations", fixture("locations_empty.json"))
            waitUntilAtLeastOneExists(hasText("Home"), timeoutMillis = 5_000)
            onNodeWithContentDescription("Add storage location").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // StorageOverviewScreen shows empty-state message
            waitUntilAtLeastOneExists(hasText("No storage locations yet", substring = true), timeoutMillis = 5_000)
            onNodeWithText("No storage locations yet. Tap + to add a fridge, freezer or pantry.").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_shows_welcome_dialog_when_no_households() {
        // User is authenticated but belongs to no households
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)

            // Welcome dialog should appear since no households
            waitUntilAtLeastOneExists(hasText("Welcome!", substring = true), timeoutMillis = 5_000)
            onNodeWithText("Welcome!").assertIsDisplayed()
            onNodeWithText("Create household").assertIsDisplayed()
            onNodeWithText("Join with invite").assertIsDisplayed()
        }
    }
}
