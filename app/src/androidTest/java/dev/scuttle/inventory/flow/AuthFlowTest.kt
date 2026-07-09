@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
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

            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)
            onNodeWithTag(DASHBOARD_TITLE_TEST_TAG).assertIsDisplayed()
        }
    }

    @Test
    fun wrong_credentials_show_user_friendly_error() {
        mockServer.enqueue("""{"message":"Invalid credentials."}""", code = 401)

        composeRule.apply {
            onNodeWithText("Email").performTextInput("wrong@example.com")
            onNodeWithText("Password").performTextInput("wrong")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Incorrect email or password."), timeoutMillis = 5_000)
            onNodeWithText("Incorrect email or password.").assertIsDisplayed()
        }
    }

    @Test
    fun server_error_shows_friendly_message() {
        mockServer.enqueue("""{"message":"Internal Server Error"}""", code = 500)

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("Server error. Please try again later."), timeoutMillis = 5_000)
            onNodeWithText("Server error. Please try again later.").assertIsDisplayed()
        }
    }

    @Test
    fun duplicate_email_on_register_shows_friendly_message() {
        composeRule.apply {
            // Switch to register mode
            onNodeWithText("New here? Create an account").performClick()
            waitUntilAtLeastOneExists(hasText("Name"), timeoutMillis = 3_000)

            mockServer.enqueue("""{"message":"Email already taken."}""", code = 409)

            onNodeWithText("Name").performTextInput("Test User")
            onNodeWithText("Email").performTextInput("existing@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Create account").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasText("An account with this email already exists."), timeoutMillis = 5_000)
            onNodeWithText("An account with this email already exists.").assertIsDisplayed()
        }
    }
}
