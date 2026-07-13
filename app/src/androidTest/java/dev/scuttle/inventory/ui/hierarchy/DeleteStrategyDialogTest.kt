package dev.scuttle.inventory.ui.hierarchy

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * `DeleteStrategyDialog` gates every destructive shelf/location delete, but its
 * safety behaviors had zero test coverage: a reviewer deleted the option filter
 * (guard a, line ~72) and the confirm-enablement check (guard b, line ~80-81)
 * at the same time and the full 149-test suite stayed green. These tests exist
 * to catch that class of regression — rendered in isolation, the same way
 * `ProductFilterSortRowTest` renders a single composable under `createComposeRule`
 * rather than driving a full screen.
 */
class DeleteStrategyDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun <T : Any> render(
        plan: DeletePlan,
        options: List<StrategyOption<T>>,
        targets: List<MoveTarget>,
        onConfirm: (T?, Long?) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            MaterialTheme {
                DeleteStrategyDialog(
                    plan = plan,
                    options = options,
                    targets = targets,
                    onDismiss = {},
                    onConfirm = onConfirm,
                )
            }
        }
    }

    @Test
    fun move_option_is_not_offered_when_there_are_no_targets() {
        // Deleting the household's only location: there is nowhere to move the
        // contents to, so "move" must not be offered as a dead option (guard a).
        render(
            plan = DeletePlan(itemCount = 1, productCount = 3, contentCount = 1, hasOtherTargets = false),
            options = locationStrategyOptions(),
            targets = emptyList(),
        )

        composeRule.onNodeWithTag("delete-strategy-${LocationDeleteStrategy.MOVE_CONTENTS}").assertDoesNotExist()
        // The dialog still offers a real (non-dead) choice, not an empty list.
        composeRule.onNodeWithTag("delete-strategy-${LocationDeleteStrategy.DELETE_CONTENTS}").assertIsEnabled()
    }

    @Test
    fun confirm_stays_disabled_when_every_offered_option_needs_a_target_that_does_not_exist() {
        // A defensive scenario the generic dialog must survive even though
        // neither shipped factory triggers it today: every option on offer
        // requires a target, and none exists. Guard (a) empties the option
        // list (selected == null); guard (b) is the second line of defense
        // that must independently keep confirm disabled if guard (a) is ever
        // weakened. Without either guard, the user could tap Delete and the
        // server would 422 on a null target.
        val onlyOptionNeedsATarget =
            listOf(
                StrategyOption(
                    strategy = LocationDeleteStrategy.MOVE_CONTENTS,
                    labelRes = R.string.delete_strategy_move,
                    requiresTarget = true,
                ),
            )

        render(
            plan = DeletePlan(itemCount = 1, productCount = 3, contentCount = 1, hasOtherTargets = false),
            options = onlyOptionNeedsATarget,
            targets = emptyList(),
        )

        composeRule.onNodeWithTag("delete-strategy-confirm").assertIsNotEnabled()
    }

    @Test
    fun confirm_is_enabled_once_a_target_is_available_for_the_selected_move() {
        // Sanity/positive counterpart to the two tests above: once a target
        // exists for the selected move option, confirm is enabled — the
        // dialog isn't stuck disabled forever.
        render(
            plan = DeletePlan(itemCount = 1, productCount = 3, contentCount = 1, hasOtherTargets = true),
            options = locationStrategyOptions(),
            targets = listOf(MoveTarget(id = 99L, name = "Pantry")),
        )

        composeRule.onNodeWithTag("delete-strategy-confirm").assertIsEnabled()
    }

    @Test
    fun an_empty_container_renders_a_plain_confirm_and_emits_null_null() {
        // An empty container needs no strategy — but it must still be a real
        // confirm dialog, not a silent delete. Confirming must send (null,
        // null) regardless of whatever the (hidden, unused) radio state would
        // have defaulted to.
        var confirmedStrategy: LocationDeleteStrategy? = LocationDeleteStrategy.DELETE_CONTENTS
        var confirmedTarget: Long? = 1L
        var confirmCount = 0

        render(
            plan = DeletePlan(itemCount = 2, productCount = 0, contentCount = 0, hasOtherTargets = true),
            options = locationStrategyOptions(),
            targets = listOf(MoveTarget(id = 99L, name = "Pantry")),
            onConfirm = { strategy, target ->
                confirmCount++
                confirmedStrategy = strategy
                confirmedTarget = target
            },
        )

        // No strategy question is shown for an empty container.
        composeRule.onNodeWithTag("delete-strategy-${LocationDeleteStrategy.MOVE_CONTENTS}").assertDoesNotExist()
        composeRule.onNodeWithTag("delete-strategy-${LocationDeleteStrategy.DELETE_CONTENTS}").assertDoesNotExist()

        composeRule.onNodeWithTag("delete-strategy-confirm").assertIsEnabled().performClick()

        assertEquals(1, confirmCount)
        assertEquals(null, confirmedStrategy)
        assertEquals(null, confirmedTarget)
    }
}
