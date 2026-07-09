@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import org.junit.Test

@HiltAndroidTest
class HouseholdFlowTest : FlowTestBase() {

    @Test
    fun create_household_appears_in_list() {
        // Login with empty households
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Create household"), timeoutMillis = 5_000)

            // Tap the "Create household" button in the welcome dialog → Households screen
            mockServer.route("/households", fixture("households_empty.json"))
            onNodeWithText("Create household").performClick()
            waitForIdle()

            // Tap the FAB to open the bottom sheet
            onNodeWithContentDescription("Create household").performClick()
            waitForIdle()

            // Fill in the household name in the bottom sheet
            mockServer.route("/households", fixture("household_created.json"))
            mockServer.route("/households", fixture("households_two.json"))
            onNodeWithText("Household name").performTextInput("Office")
            onNodeWithText("Create").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Not onNodeWithText: the drawer (always composed even while closed)
            // also echoes every household name as plain text, so "Office" matches
            // twice. The household card's contentDescription is unique to this screen.
            onNodeWithContentDescription("Office").assertIsDisplayed()
        }
    }
}
