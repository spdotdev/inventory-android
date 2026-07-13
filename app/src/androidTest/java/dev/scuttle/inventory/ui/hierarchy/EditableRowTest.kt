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

    private fun render(
        editMode: Boolean,
        isSystem: Boolean,
        selected: Boolean = false,
        canMoveUp: Boolean = true,
        canMoveDown: Boolean = true,
        onClick: () -> Unit = {},
        onRename: () -> Unit = {},
        onMoveUp: () -> Unit = {},
        onMoveDown: () -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                EditableRow(
                    name = "Top shelf",
                    editMode = editMode,
                    isSystem = isSystem,
                    selected = selected,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onClick = onClick,
                    onRename = onRename,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                )
            }
        }
    }

    @Test
    fun outside_edit_mode_shows_no_edit_affordances_but_the_row_is_still_tappable() {
        var clicked = false
        render(editMode = false, isSystem = false, onClick = { clicked = true })

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
            editMode = true,
            isSystem = false,
            onRename = { renamed = true },
            onMoveUp = { movedUp = true },
            onMoveDown = { movedDown = true },
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
        render(editMode = true, isSystem = true)

        composeRule.onNodeWithContentDescription("Rename shelf").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Move shelf up").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Move shelf down").assertDoesNotExist()
    }
}
