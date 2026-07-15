package dev.scuttle.inventory.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.R

/**
 * The multi-household picker (final review, Blocker 2 — reintroduced from git history,
 * `git show c372925^:app/src/main/java/dev/scuttle/inventory/MainActivity.kt`, where it
 * lived inline before Task 7's bottom-nav rework deleted it along with the Search tab).
 *
 * Every entry point that opens Search from a GLOBAL action — one with no specific row or
 * household already in hand (Dashboard's top-bar icon and products stat card, Missing
 * items' top-bar icon, the bottom-bar Scan tab's LOOKUP mode) — used to hard-code the
 * FIRST household instead of asking. That silently made every household but the first
 * unreachable from those entry points: the only working path to a second household's
 * search was Home → that household's own "+" icon → Storage overview → its search icon.
 * Callers must only show this when there is more than one household (skip it and
 * navigate directly otherwise) — a picker over a single option is just an extra tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdPickerSheet(
    households: List<HouseholdOption>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.search_choose_household_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            households.forEach { household ->
                NavigationDrawerItem(
                    label = { Text(household.name) },
                    selected = false,
                    onClick = { onPick(household.id) },
                    modifier =
                        Modifier
                            .padding(horizontal = 12.dp)
                            .testTag("household-picker-${household.name}"),
                )
            }
        }
    }
}
