@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
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

@HiltAndroidTest
class EditProductDetailFlowTest : FlowTestBase() {
    @Test
    fun edit_product_name_and_save() {
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

            // Storage tab → Fridge (LocationDetailScreen)
            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            // Tap Milk → ProductDetailScreen
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            waitUntilAtLeastOneExists(hasTestTag("product-1000"), timeoutMillis = 5_000)
            onNodeWithTag("product-1000").clickNameArea()

            // Clear the editable name field and type new name, then save — PUT .../products/1000
            // [0] = Name field (first OutlinedTextField on screen)
            waitUntilAtLeastOneExists(hasSetTextAction(), timeoutMillis = 8_000)
            mockServer.enqueue(fixture("product_renamed.json"))
            onAllNodes(hasSetTextAction())[0].performTextClearance()
            onAllNodes(hasSetTextAction())[0].performTextInput("Oat Milk")
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Save"), timeoutMillis = 5_000)
            onNodeWithText("Save").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Oat Milk"), timeoutMillis = 5_000)
            onAllNodesWithText("Oat Milk")[0].assertIsDisplayed()
        }
    }
}
