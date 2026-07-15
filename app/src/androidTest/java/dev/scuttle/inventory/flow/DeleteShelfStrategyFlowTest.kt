@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory.flow

import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import dagger.hilt.android.testing.HiltAndroidTest
import dev.scuttle.inventory.FlowTestBase
import dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy
import dev.scuttle.inventory.ui.dashboard.DASHBOARD_TITLE_TEST_TAG
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The delete-strategy dialog for SHELVES, driven through the real screen — the
 * companion to `DeleteLocationFlowTest` one level up. `DeleteShelfFlowTest`
 * (Task 4) only ever exercises the EMPTY-shelf path (shelves_two.json's shelves
 * both default `product_count` to 0, so `DeletePlan.needsStrategy` is false and
 * the dialog renders as a plain confirm with no radios — see its own doc
 * comment). These tests are the ones that actually drive a shelf with contents
 * through the real UI, which is the case the strategy dialog exists for.
 *
 * All four tests share one household/location (1 / 10 "Fridge") and drain
 * `mockServer.server`'s recorded requests afterwards to assert on the actual
 * wire bytes — several of the guarantees below are about what the client sends,
 * not just what renders.
 */
@HiltAndroidTest
class DeleteShelfStrategyFlowTest : FlowTestBase() {
    @Test
    fun deleting_a_shelf_with_products_requires_choosing_a_strategy_and_the_wire_body_omits_null_keys() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            // Storage tab → Fridge → LocationDetailScreen
            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 5_000)

            // Edit mode, select "Top shelf" (the only shelf — no other shelf exists
            // to move products to, so this also proves "move" is never offered when
            // there is nowhere to move to — guard (a) in DeleteStrategyDialog).
            onNodeWithContentDescription("Edit shelves").performClick()
            waitForIdle()
            // Wait for the edit-mode LIST row specifically, not just the text (which
            // also exists on the tab this view just replaced) — see DeleteShelfFlowTest
            // for the full race this closes.
            waitUntilAtLeastOneExists(hasTestTag("shelf-row-100"), timeoutMillis = 5_000)
            // clickNameArea(), not performClick(): see FlowTestBase.clickNameArea's
            // doc — a node-center tap on this row lands on the trailing "Rename
            // shelf" icon button on some screen sizes (confirmed via a printToLog
            // dump on real CI hardware), silently opening that dialog instead of
            // toggling selection.
            onNodeWithTag("shelf-row-100").clickNameArea()
            waitForIdle()

            // Delete (1) only OPENS the dialog. requestDelete() refreshes the shelf
            // list first, off a fresh product_count, before building the plan.
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
            onNodeWithText("Delete (1)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 1 item(s)?"), timeoutMillis = 3_000)

            // A non-empty shelf renders the strategy question — not a plain confirm.
            onNodeWithText("1 product(s) are stored inside. What should happen to them?").assertIsDisplayed()
            // No other shelf exists, so "move" must not be offered as a dead option.
            onNodeWithText("Move them somewhere else").assertDoesNotExist()
            onNodeWithText("Keep them here, in Unsorted").assertIsDisplayed()

            // Explicitly choose "Delete them too" — the destructive option is never
            // applied silently; the user has to tap it (it isn't the safest default,
            // which is "Keep them here, in Unsorted").
            onNodeWithText("Delete them too").performClick()
            waitForIdle()

            mockServer.route("/households/1/locations/10/shelves/100", "", code = 204)
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_empty.json"))
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilDoesNotExist(hasText("Top shelf"), timeoutMillis = 5_000)
            // The Undo snackbar is the completion of the confirmation gate: a
            // destructive delete is never the last word without a way back.
            waitUntilAtLeastOneExists(hasText("Shelves deleted."), timeoutMillis = 5_000)
            onNodeWithText("Undo").assertIsDisplayed()
        }

        val deleteRequest = drainDeleteRequest(path = "/households/1/locations/10/shelves/100")
        val body = deleteRequest.body.readUtf8()
        val expectedStrategy = "\"strategy\":\"${ShelfDeleteStrategy.DELETE_PRODUCTS.wire}\""
        assert(body.contains(expectedStrategy)) { "strategy missing/wrong — body was: $body" }
        assert(!body.contains("target_shelf_id")) { "target_shelf_id must be OMITTED, not null — body was: $body" }
        assert(Regex(""""deletion_batch_id":"[^"]+"""").containsMatchIn(body)) {
            "deletion_batch_id missing — body was: $body"
        }
    }

    @Test
    fun the_unsorted_shelf_cannot_be_selected_and_is_never_offered_as_a_move_target() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_with_unsorted.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        mockServer.route("/households/1/shelves/900/products", fixture("products_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_with_unsorted.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            mockServer.route("/households/1/shelves/900/products", fixture("products_empty.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 5_000)

            onNodeWithContentDescription("Edit shelves").performClick()
            waitForIdle()
            // Wait for the edit-mode LIST rows specifically, not just their text
            // (which also exists on the tabs this view just replaced) — see
            // DeleteShelfFlowTest for the full race this closes.
            waitUntilAtLeastOneExists(hasTestTag("shelf-row-900"), timeoutMillis = 5_000)

            // Tapping the system shelf must be a no-op selection-wise: the Delete
            // button stays the plain, unselected "Delete" (no count), and disabled.
            // clickNameArea() — see FlowTestBase's doc for why performClick()'s
            // node-center tap is unsafe on an EditableRow.
            onNodeWithTag("shelf-row-900").clickNameArea()
            waitForIdle()
            onNodeWithText("Delete").assertIsDisplayed()

            // Now select the real shelf and open the dialog.
            onNodeWithTag("shelf-row-100").clickNameArea()
            waitForIdle()
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_with_unsorted.json"))
            onNodeWithText("Delete (1)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 1 item(s)?"), timeoutMillis = 3_000)

            // "Unsorted" is a live shelf that isn't selected — if it were eligible as
            // a move target, "move" would be offered. It must not be: Unsorted is
            // never a destination for another shelf's contents.
            onNodeWithText("Move them somewhere else").assertDoesNotExist()
            onNodeWithText("Keep them here, in Unsorted").assertIsDisplayed()
            onNodeWithText("Delete them too").assertIsDisplayed()

            // The DELETE itself must get its own dedicated route: MockWebServerRule
            // dispatches by PATH PREFIX only (no method awareness — see its own doc
            // comment), and "/households/1/locations/10/shelves/100" is a prefix
            // match for the plain "/households/1/locations/10/shelves" GET route
            // too. Without this, the DELETE consumes the GET-intended response below
            // (as a 200 with a shelf-list body), leaving confirmDelete()'s own
            // post-delete re-list GET with nothing queued — a fallback 500 that
            // never updates state.shelves, so "Top shelf" never disappears.
            mockServer.route("/households/1/locations/10/shelves/100", "", code = 204)
            // confirmDelete() re-lists the shelves on success, off the fresh
            // post-delete state — Top shelf gone, Unsorted still there.
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_unsorted_only.json"))
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilDoesNotExist(hasText("Top shelf"), timeoutMillis = 5_000)
            // Unsorted itself survives — it was never a candidate for deletion.
            onNodeWithText("Unsorted").assertExists()
        }
    }

    @Test
    fun undo_restores_a_shelf_deleted_with_the_keep_products_strategy_along_with_its_products() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 5_000)
            // The product is visible before the delete — the baseline "with its
            // products" this test proves Undo returns to.
            waitUntilAtLeastOneExists(hasText("Milk"), timeoutMillis = 5_000)

            onNodeWithContentDescription("Edit shelves").performClick()
            waitForIdle()
            // Wait for the edit-mode LIST row specifically, not just the text (which
            // also exists on the tab this view just replaced) — see DeleteShelfFlowTest
            // for the full race this closes.
            waitUntilAtLeastOneExists(hasTestTag("shelf-row-100"), timeoutMillis = 5_000)
            // clickNameArea() — see FlowTestBase's doc for why performClick()'s
            // node-center tap is unsafe on an EditableRow.
            onNodeWithTag("shelf-row-100").clickNameArea()
            waitForIdle()

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
            onNodeWithText("Delete (1)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 1 item(s)?"), timeoutMillis = 3_000)

            // The non-destructive strategy: the products are NOT deleted, only the
            // shelf. This is the strategy Undo must restore products for — a
            // destructive delete_products undo can't come back "with its products"
            // because the server destroyed them too.
            onNodeWithText("Keep them here, in Unsorted").performClick()
            waitForIdle()

            mockServer.route("/households/1/locations/10/shelves/100", "", code = 204)
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_empty.json"))
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilDoesNotExist(hasText("Top shelf"), timeoutMillis = 5_000)
            waitUntilAtLeastOneExists(hasText("Undo"), timeoutMillis = 5_000)

            // Tap the snackbar's Undo action. restore() hits POST
            // /households/1/restore/{batch} — the route is registered by PREFIX so
            // it matches regardless of the client-minted batch id's actual value.
            mockServer.route("/households/1/restore/", fixture("restore_ok.json"))
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_one_with_products.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            onNodeWithText("Undo").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            // The shelf is back, and — the actual guarantee — so is its product.
            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 5_000)
            waitUntilAtLeastOneExists(hasText("Milk"), timeoutMillis = 5_000)
        }
    }

    @Test
    fun deleting_two_shelves_in_one_gesture_shares_one_batch_id_across_both_requests() {
        mockServer.enqueue(fixture("auth_login.json"))
        mockServer.route("/households", fixture("households_one.json"))
        mockServer.route("/households/1/locations", fixture("locations_one.json"))
        mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two_with_products.json"))
        mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
        mockServer.route("/households/1/shelves/101/products", fixture("products_empty.json"))

        composeRule.apply {
            onNodeWithText("Email").performTextInput("test@example.com")
            onNodeWithText("Password").performTextInput("password123")
            onAllNodesWithText("Sign in").filterToOne(hasClickAction()).performClick()

            Thread.sleep(3_000)
            waitUntilAtLeastOneExists(hasTestTag(DASHBOARD_TITLE_TEST_TAG), timeoutMillis = 5_000)

            onNodeWithTag("bottom-nav-home").performClick()
            waitUntilAtLeastOneExists(hasTestTag("home-location-Fridge"), timeoutMillis = 8_000)

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two_with_products.json"))
            mockServer.route("/households/1/shelves/100/products", fixture("products_one.json"))
            mockServer.route("/households/1/shelves/101/products", fixture("products_empty.json"))
            onNodeWithTag("home-location-Fridge").performClick()
            waitForIdle()

            waitUntilAtLeastOneExists(hasText("Top shelf"), timeoutMillis = 5_000)

            onNodeWithContentDescription("Edit shelves").performClick()
            waitForIdle()
            // Wait for the edit-mode LIST rows specifically, not just their text
            // (which also exists on the tabs this view just replaced) — see
            // DeleteShelfFlowTest for the full race this closes.
            waitUntilAtLeastOneExists(hasTestTag("shelf-row-100"), timeoutMillis = 5_000)
            waitUntilAtLeastOneExists(hasTestTag("shelf-row-101"), timeoutMillis = 5_000)

            // Select BOTH shelves — one gesture, two items. clickNameArea() — see
            // FlowTestBase's doc for why performClick()'s node-center tap is unsafe
            // on an EditableRow.
            onNodeWithTag("shelf-row-100").clickNameArea()
            waitForIdle()
            onNodeWithTag("shelf-row-101").clickNameArea()
            waitForIdle()

            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_two_with_products.json"))
            onNodeWithText("Delete (2)").performClick()
            waitForIdle()
            waitUntilAtLeastOneExists(hasText("Delete 2 item(s)?"), timeoutMillis = 3_000)

            onNodeWithText("Delete them too").performClick()
            waitForIdle()

            mockServer.route("/households/1/locations/10/shelves/100", "", code = 204)
            mockServer.route("/households/1/locations/10/shelves/101", "", code = 204)
            mockServer.route("/households/1/locations/10/shelves", fixture("shelves_empty.json"))
            onNodeWithTag("delete-strategy-confirm").performClick()
            Thread.sleep(2_000)
            waitForIdle()

            waitUntilDoesNotExist(hasText("Top shelf"), timeoutMillis = 5_000)
            waitUntilDoesNotExist(hasText("Middle shelf"), timeoutMillis = 5_000)
        }

        val first = drainDeleteRequest(path = "/households/1/locations/10/shelves/100")
        val second = drainDeleteRequest(path = "/households/1/locations/10/shelves/101")
        val firstBatch = first.body.readUtf8().batchId()
        val secondBatch = second.body.readUtf8().batchId()
        requireNotNull(firstBatch) { "shelf 100's delete carried no deletion_batch_id" }
        assert(firstBatch == secondBatch) {
            "the two deletes in one gesture must share ONE batch id — got $firstBatch and $secondBatch"
        }
    }

    /** Drains the recorded request queue for the DELETE that hit [path] exactly. */
    private fun drainDeleteRequest(path: String): RecordedRequest {
        val request =
            generateSequence { mockServer.server.takeRequest(1, TimeUnit.SECONDS) }
                .firstOrNull { it.method == "DELETE" && it.path == path }
        return requireNotNull(request) { "no DELETE was ever recorded for $path" }
    }

    private fun String.batchId(): String? = Regex(""""deletion_batch_id":"([^"]+)"""").find(this)?.groupValues?.get(1)
}
