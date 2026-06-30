@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.isToggleable
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
class MandatoryToggleFlowTest : FlowTestBase() {

    @Test
    fun toggle_mandatory_and_save() {
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

            // Drawer → Fridge
            onNodeWithContentDescription("Open menu").performClick()
            waitUntilAtLeastOneExists(hasText("Fridge").and(hasClickAction()), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onAllNodesWithText("Fridge").filterToOne(hasClickAction()).performClick()
            waitForIdle()

            // Tap Milk → ProductDetailScreen
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            waitUntilAtLeastOneExists(hasText("Milk"), timeoutMillis = 5_000)
            onNodeWithText("Milk").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // Mandatory toggle starts OFF (is_mandatory: false in products_one.json) — toggle it ON
            // Use isToggleable() to target the Switch node, not the sibling Text label
            waitUntilAtLeastOneExists(hasText("Mandatory on this shelf"), timeoutMillis = 5_000)
            onAllNodes(isToggleable())[0].assertIsOff()
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            onAllNodes(isToggleable())[0].assertIsOn()

            // Save → PUT /households/1/shelves/100/products/1000
            mockServer.enqueue(fixture("product_mandatory.json"))
            onNodeWithText("Save").performClick()
            Thread.sleep(1_500)
            waitForIdle()
        }
    }
}
