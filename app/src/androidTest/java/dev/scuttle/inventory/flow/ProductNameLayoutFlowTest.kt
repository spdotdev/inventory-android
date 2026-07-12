@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.width
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import org.junit.Test

/**
 * Regression cover for #31: the product name shared one Row with the quantity stepper and the
 * move button, so it only ever got the width those controls left over — a few characters wide,
 * and narrower still in Dutch, where "Verplaatsen" is 7 characters longer than "Move". The name
 * must own the card's full width, independent of how wide the action controls happen to be in
 * the current locale.
 */
@HiltAndroidTest
class ProductNameLayoutFlowTest : FlowTestBase() {
    private fun loginAndNavigateToShelf() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_long_name.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 8_000)

            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_long_name.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitUntilAtLeastOneExists(hasTestTag("product-1000"), timeoutMillis = 8_000)
        }
    }

    /**
     * The structural invariant: the name is laid out against the card's width, not against
     * whatever the stepper and move button leave behind. Locale- and font-scale-independent,
     * which is the point — the Dutch build broke precisely because the old layout was neither.
     */
    @Test
    fun product_name_gets_the_full_card_width() {
        loginAndNavigateToShelf()

        // Unmerged tree throughout: the card is clickable, so it merges its descendants'
        // semantics — onNodeWithText would otherwise match the card itself and measure it
        // against its own width, which passes no matter how squashed the name is.
        val cardWidth = composeRule.onNodeWithTag("product-1000").getUnclippedBoundsInRoot().width
        val nameWidth =
            composeRule
                .onNodeWithText("Chateaubriand", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .width

        assert(nameWidth >= cardWidth * 0.8f) {
            "Product name got $nameWidth of a $cardWidth card — it is still competing with the " +
                "action controls for horizontal space (#31)"
        }
    }

    /** What the reporter actually asked for: a normal product name reads on one line. */
    @Test
    fun product_name_renders_on_a_single_unellipsized_line() {
        loginAndNavigateToShelf()

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText("Chateaubriand", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        val layout = layouts.first()

        assert(layout.lineCount == 1) { "Name wrapped onto ${layout.lineCount} lines (#31)" }
        assert(!layout.hasVisualOverflow) { "Name was truncated/ellipsized (#31)" }
    }
}
