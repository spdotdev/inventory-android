package dev.scuttle.inventory.ui.hierarchy

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `EditableRow` is the row-level gate for "the Unsorted shelf cannot be renamed
 * or reordered": the pencil and up/down buttons must not even render for it,
 * regardless of edit mode. (The companion invariant "cannot be SELECTED" is
 * enforced independently in `ShelvesViewModel.toggleSelection` — see
 * `ShelvesViewModelTest.the_unsorted_shelf_cannot_be_selected_for_deletion` —
 * since this row always forwards a tap to `onClick` and lets the ViewModel be
 * the single source of truth for that rule.)
 *
 * Rendered in isolation, same pattern as `DeleteStrategyDialogTest` /
 * `ProductFilterSortRowTest`: `createComposeRule()`, no Activity/Hilt.
 */
class EditableRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Groups every knob EditableRow itself exposes (the composable is exempt from
    // LongParameterList as declarative UI, per detekt.yml; this is the same
    // parameter surface one level removed, forwarded verbatim by the render() test
    // helper below — a data class is detekt's own idiom for that, not a lint dodge:
    // LongParameterList.ignoreDataClasses is on by default).
    private data class RenderState(
        val editMode: Boolean,
        val isSystem: Boolean,
        val selected: Boolean = false,
        val canMoveUp: Boolean = true,
        val canMoveDown: Boolean = true,
        val onClick: () -> Unit = {},
        val onRename: () -> Unit = {},
        val onMoveUp: () -> Unit = {},
        val onMoveDown: () -> Unit = {},
    )

    private fun render(state: RenderState) {
        composeRule.setContent {
            MaterialTheme {
                EditableRow(
                    name = "Top shelf",
                    editMode = state.editMode,
                    isSystem = state.isSystem,
                    selected = state.selected,
                    canMoveUp = state.canMoveUp,
                    canMoveDown = state.canMoveDown,
                    onClick = state.onClick,
                    onRename = state.onRename,
                    onMoveUp = state.onMoveUp,
                    onMoveDown = state.onMoveDown,
                )
            }
        }
    }

    @Test
    fun outside_edit_mode_shows_no_edit_affordances_but_the_row_is_still_tappable() {
        var clicked = false
        render(RenderState(editMode = false, isSystem = false, onClick = { clicked = true }))

        composeRule.onNodeWithContentDescription("Rename shelf").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Move shelf up").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Move shelf down").assertDoesNotExist()

        composeRule.onNodeWithText("Top shelf").performClick()
        assertTrue(clicked)
    }

    @Test
    fun edit_mode_shows_rename_and_reorder_for_a_normal_shelf() {
        var renamed = false
        var movedUp = false
        var movedDown = false
        render(
            RenderState(
                editMode = true,
                isSystem = false,
                onRename = { renamed = true },
                onMoveUp = { movedUp = true },
                onMoveDown = { movedDown = true },
            ),
        )

        composeRule.onNodeWithContentDescription("Rename shelf").performClick()
        composeRule.onNodeWithContentDescription("Move shelf up").performClick()
        composeRule.onNodeWithContentDescription("Move shelf down").performClick()

        assertTrue(renamed)
        assertTrue(movedUp)
        assertTrue(movedDown)
    }

    @Test
    fun the_unsorted_shelf_shows_no_rename_or_reorder_even_in_edit_mode() {
        render(RenderState(editMode = true, isSystem = true))

        composeRule.onNodeWithContentDescription("Rename shelf").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Move shelf up").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Move shelf down").assertDoesNotExist()
    }
}
