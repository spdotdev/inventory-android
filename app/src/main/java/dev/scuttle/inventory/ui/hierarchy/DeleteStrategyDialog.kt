package dev.scuttle.inventory.ui.hierarchy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.hierarchy.LocationDeleteStrategy
import dev.scuttle.inventory.data.hierarchy.ShelfDeleteStrategy

/**
 * One radio choice offered by [DeleteStrategyDialog]: the wire value it sends
 * ([strategy]), the copy that names it ([labelRes]), and whether picking it
 * requires a [MoveTarget] ([requiresTarget] — true only for the MOVE_* member
 * of either strategy enum).
 */
data class StrategyOption<T>(
    val strategy: T,
    @StringRes val labelRes: Int,
    val requiresTarget: Boolean,
)

/** A shelf or location the contents of a deleted container could move to. */
data class MoveTarget(
    val id: Long,
    val name: String,
)

/**
 * Asks what to do with the contents of the shelves/locations being deleted.
 *
 * One dialog per GESTURE, not per item: the summary covers the whole batch and
 * one choice applies to all of it. Generic in the strategy type so ONE dialog
 * serves both levels — shelves resolve with [ShelfDeleteStrategy] (which has a
 * third, non-destructive "unsort" member), locations with [LocationDeleteStrategy]
 * (which deliberately has no unsort: "unsorted" still means IN the location, and
 * the location is the thing being deleted). Use [shelfStrategyOptions] /
 * [locationStrategyOptions] to build [options] for each caller.
 *
 * When [plan] doesn't need a strategy (an empty container), this renders as a
 * plain confirm — no radios, nothing to choose. It is still a confirm: an empty
 * shelf/location is never deleted without one.
 */
@Composable
fun <T : Any> DeleteStrategyDialog(
    plan: DeletePlan,
    options: List<StrategyOption<T>>,
    targets: List<MoveTarget>,
    onDismiss: () -> Unit,
    onConfirm: (T?, Long?) -> Unit,
) {
    // Never offer a MOVE_* option when there is nowhere to move to — the caller
    // may pass an option list that includes a move, so this is enforced here.
    val availableOptions = options.filter { !it.requiresTarget || targets.isNotEmpty() }

    // Options are supplied safest-first (move > unsort > delete), so the first
    // available one is always the safest available default — never destroying
    // data by default, without this dialog having to know the concrete enum.
    var selected by remember(plan, availableOptions) { mutableStateOf(availableOptions.firstOrNull()) }
    var targetId by remember(plan, targets) { mutableStateOf(targets.firstOrNull()?.id) }

    val confirmEnabled =
        !plan.needsStrategy || selected?.let { !it.requiresTarget || targetId != null } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_strategy_title, plan.itemCount)) },
        text =
            if (plan.needsStrategy) {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.delete_strategy_summary, plan.productCount))

                        availableOptions.forEach { option ->
                            StrategyOptionRow(
                                option = option,
                                selected = selected == option,
                                tag = "delete-strategy-${option.strategy}",
                                onSelect = { selected = option },
                            )
                        }

                        if (options.any { it.requiresTarget } && targets.isEmpty()) {
                            Text(stringResource(R.string.delete_strategy_no_target))
                        }

                        if (selected?.requiresTarget == true && targets.isNotEmpty()) {
                            Text(stringResource(R.string.delete_strategy_pick_target))
                            targets.forEach { target ->
                                TargetOptionRow(
                                    target = target,
                                    selected = targetId == target.id,
                                    onSelect = { targetId = target.id },
                                )
                            }
                        }
                    }
                }
            } else {
                null
            },
        confirmButton = {
            Button(
                onClick = {
                    // An empty container needs no strategy at all — send null/null
                    // regardless of whatever the (hidden) radio state defaulted to.
                    val strategy = if (plan.needsStrategy) selected?.strategy else null
                    val target = if (plan.needsStrategy && selected?.requiresTarget == true) targetId else null
                    onConfirm(strategy, target)
                },
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("delete-strategy-confirm"),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun <T> StrategyOptionRow(
    option: StrategyOption<T>,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .selectable(selected = selected, onClick = onSelect)
                .testTag(tag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Text(stringResource(option.labelRes))
        }
    }
}

@Composable
private fun TargetOptionRow(
    target: MoveTarget,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .selectable(selected = selected, onClick = onSelect)
                .testTag("delete-target-${target.id}"),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(target.name)
    }
}

/**
 * The three ways to resolve a shelf's products, safest first: move them to
 * another shelf, leave them in this location's "Unsorted" holding shelf, or
 * delete them along with the shelf.
 */
fun shelfStrategyOptions(): List<StrategyOption<ShelfDeleteStrategy>> =
    listOf(
        StrategyOption(
            strategy = ShelfDeleteStrategy.MOVE_PRODUCTS,
            labelRes = R.string.delete_strategy_move,
            requiresTarget = true,
        ),
        StrategyOption(
            strategy = ShelfDeleteStrategy.UNSORT_PRODUCTS,
            labelRes = R.string.delete_strategy_unsort,
            requiresTarget = false,
        ),
        StrategyOption(
            strategy = ShelfDeleteStrategy.DELETE_PRODUCTS,
            labelRes = R.string.delete_strategy_delete,
            requiresTarget = false,
        ),
    )

/**
 * The two ways to resolve a location's contents, safest first. No "unsort"
 * member exists here on purpose: "unsorted" means off-shelf but still IN the
 * location, and the location itself is what's being deleted.
 */
fun locationStrategyOptions(): List<StrategyOption<LocationDeleteStrategy>> =
    listOf(
        StrategyOption(
            strategy = LocationDeleteStrategy.MOVE_CONTENTS,
            labelRes = R.string.delete_strategy_move,
            requiresTarget = true,
        ),
        StrategyOption(
            strategy = LocationDeleteStrategy.DELETE_CONTENTS,
            labelRes = R.string.delete_strategy_delete,
            requiresTarget = false,
        ),
    )
