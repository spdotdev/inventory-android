@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class LogoutFlowTest : FlowTestBase() {
    @Test
    fun sign_out_returns_to_login_screen() {
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

            // Dashboard's settings gear → SettingsScreen
            onNodeWithContentDescription("Settings").performClick()
            waitForIdle()

            // Tap "Sign out" to open the confirm dialog. Scroll first: the bottom
            // nav costs the settings screen height, pushing the button below the
            // fold on small CI-emulator screens (clipped clicks miss).
            onNodeWithText("Sign out").performScrollTo().performClick()
            waitForIdle()
            // Dialog appears — now 2 "Sign out" nodes exist (button + dialog confirm); pick the dialog one [1]
            mockServer.enqueueEmpty(code = 204) // POST /auth/logout
            waitUntilAtLeastOneExists(hasText("Sign out?"), timeoutMillis = 3_000)
            onAllNodesWithText("Sign out")[1].performClick()

            Thread.sleep(2_000)
            waitUntilAtLeastOneExists(hasText("Sign in"), timeoutMillis = 5_000)
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).assertIsDisplayed()
        }
    }
}
