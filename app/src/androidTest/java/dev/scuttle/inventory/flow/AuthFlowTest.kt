@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import org.junit.Test

@HiltAndroidTest
class AuthFlowTest : FlowTestBase() {

    @Test
    fun login_with_email_navigates_to_dashboard() {
        mockServer.enqueue(fixture("auth_login.json"))
        // Dashboard + Drawer both call these concurrently
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            waitUntilAtLeastOneExists(hasText("Dashboard"), timeoutMillis = 5_000)
            onAllNodesWithText("Dashboard")[0].assertIsDisplayed()
        }
    }

    @Test
    fun wrong_credentials_show_error() {
        mockServer.enqueue("""{"message":"Invalid credentials."}""", code = 401)

        composeRule.apply {
            onNodeWithText("Email").performTextInput("wrong@example.com")
            onNodeWithText("Password").performTextInput("wrong")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            onRoot().printToLog("AuthFlowTest")
            waitUntilAtLeastOneExists(hasText("401", substring = true), timeoutMillis = 5_000)
            onAllNodesWithText("401", substring = true)[0].assertIsDisplayed()
        }
    }
}
