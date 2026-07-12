@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import dev.scuttle.inventory.ui.products.PRODUCT_DETAIL_TITLE_TEST_TAG
import org.junit.Test

@HiltAndroidTest
class SearchFlowTest : FlowTestBase() {
    @Test
    fun clicking_search_result_navigates_to_product_detail() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        // Twice: the dashboard prefetch (locations → shelves → products) eats the
        // first response right after login; ProductDetailViewModel.load() re-fetches
        // the same endpoint after the click and needs the second (routes are
        // consume-once queues — see MockWebServerRule).
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            mockServer.route("/households/1/search", fixture("search_results.json"))
            // Search tab is disabled until drawerUi.entries loads (HierarchyStore, async
            // after login) — tapping it before then is a no-op on a disabled node, not an
            // error, so wait for enabled first or the click silently does nothing.
            waitUntilAtLeastOneExists(hasTestTag("bottom-nav-search").and(isEnabled()), timeoutMillis = 8_000)
            onNodeWithTag("bottom-nav-search").performClick()
            waitUntilAtLeastOneExists(hasTestTag("search_field"), timeoutMillis = 5_000)

            onNodeWithTag("search_field").performTextInput("Milk")
            Thread.sleep(1_500)
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Fridge › Top shelf"), timeoutMillis = 5_000)
            // Click the result card — navigates to product detail
            onNodeWithText("Fridge › Top shelf").performClick()

            // Product detail screen shows the product name in the top bar. Not
            // hasText("Milk"): the search result card showing the same name can
            // still be composed in the previous back-stack destination during
            // the navigation transition (see search_returns_matching_product's
            // note below) — the title's distinct testTag avoids that collision.
            // Wait for the loaded name, not just the tag — the tag composes
            // immediately with the "[Product]" placeholder while load() is in flight.
            waitUntilAtLeastOneExists(
                hasTestTag(PRODUCT_DETAIL_TITLE_TEST_TAG).and(hasText("Milk")),
                timeoutMillis = 5_000,
            )
            onNodeWithTag(PRODUCT_DETAIL_TITLE_TEST_TAG).assertTextEquals("Milk")
        }
    }

    @Test
    fun search_returns_matching_product() {
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

            mockServer.route("/households/1/search", fixture("search_results.json"))
            // Search tab is disabled until drawerUi.entries loads (HierarchyStore, async
            // after login) — tapping it before then is a no-op on a disabled node, not an
            // error, so wait for enabled first or the click silently does nothing.
            waitUntilAtLeastOneExists(hasTestTag("bottom-nav-search").and(isEnabled()), timeoutMillis = 8_000)
            onNodeWithTag("bottom-nav-search").performClick()
            waitForIdle()

            // Type query in the search field
            onNodeWithTag("search_field").performTextInput("Milk")
            Thread.sleep(1_500)
            waitForIdle()

            // Assert on the path text — unique to search result card; "Milk" also appears in backstack
            waitUntilAtLeastOneExists(hasText("Fridge › Top shelf"), timeoutMillis = 5_000)
            onNodeWithText("Fridge › Top shelf").assertIsDisplayed()
        }
    }
}
