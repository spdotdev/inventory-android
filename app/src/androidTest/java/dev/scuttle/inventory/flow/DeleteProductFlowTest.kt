@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class DeleteProductFlowTest : FlowTestBase() {
    @Test
    fun swipe_to_delete_removes_product() {
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

            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasTestTag("drawer-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithTag("drawer-location-Fridge").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Swipe "Milk" left to reveal the delete action
            waitUntilAtLeastOneExists(hasText("Milk"), timeoutMillis = 5_000)
            onNodeWithText("Milk").performTouchInput { swipeLeft() }
            waitForIdle()

            // Confirm delete in the dialog
            mockServer.enqueueEmpty(code = 204) // DELETE .../products/1000
            mockServer.route("/households/1/shelves/100/products", fixture("products_empty.json"))
            waitUntilAtLeastOneExists(hasText("Delete"), timeoutMillis = 3_000)
            onAllNodesWithText("Delete").filterToOne(hasClickAction()).performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilDoesNotExist(hasText("Milk"), timeoutMillis = 5_000)
        }
    }
}
