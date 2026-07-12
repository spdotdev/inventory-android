@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import dev.scuttle.inventory.ui.settings.SETTINGS_VERSION_TEST_TAG
import org.junit.Test

/**
 * Testers are asked to quote the version from Settings in their bug reports (the
 * GitHub issue forms tell them to), so a version line that renders stale or wrong
 * values is worse than none at all — it sends us chasing bugs in the wrong build.
 * Assert it shows the ACTUAL BuildConfig values, not merely that some text is there.
 */
@HiltAndroidTest
class SettingsVersionFlowTest : FlowTestBase() {
    @Test
    fun settings_shows_the_real_build_version() {
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

            // Dashboard's settings gear -> SettingsScreen
            onNodeWithContentDescription("Settings").performClick()
            waitUntilAtLeastOneExists(hasTestTag(SETTINGS_VERSION_TEST_TAG), timeoutMillis = 5_000)

            // The line sits below Sign out — off-screen on shorter devices.
            onNodeWithTag(SETTINGS_VERSION_TEST_TAG).performScrollTo().assertIsDisplayed()
            onNodeWithTag(SETTINGS_VERSION_TEST_TAG)
                .assertTextEquals(
                    "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                )
        }
    }
}
