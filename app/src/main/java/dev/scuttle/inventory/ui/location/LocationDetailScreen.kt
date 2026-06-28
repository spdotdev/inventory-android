package dev.scuttle.inventory.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.scuttle.inventory.ui.products.ProductsPane
import dev.scuttle.inventory.ui.shelves.ShelvesViewModel
import kotlinx.coroutines.launch

/**
 * A storage location: shelves as a scrollable tab strip, products in a swipe pager
 * (one page per shelf). Mirrors the Frost design (D-020).
 */
@Composable
fun LocationDetailScreen(
    householdId: Long,
    locationId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    shelvesViewModel: ShelvesViewModel = hiltViewModel(),
) {
    val state by shelvesViewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.shelves.size })
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(householdId, locationId) {
        shelvesViewModel.load(householdId, locationId)
    }

    Column(modifier = modifier.fillMaxSize()) {
        TextButton(onClick = onBack) {
            Text("← Storage")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.newName,
                onValueChange = shelvesViewModel::onNewNameChange,
                label = { Text("New shelf") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide(); shelvesViewModel.create() }
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { keyboardController?.hide(); shelvesViewModel.create() },
                enabled = !state.loading && state.newName.isNotBlank(),
            ) {
                Text("Add shelf")
            }
        }

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (state.shelves.isEmpty()) {
            if (!state.loading) {
                Text(text = "No shelves yet. Add one above.", modifier = Modifier.padding(16.dp))
            }
        } else {
            val selected = pagerState.currentPage.coerceAtMost(state.shelves.size - 1)
            ScrollableTabRow(selectedTabIndex = selected) {
                state.shelves.forEachIndexed { index, shelf ->
                    Tab(
                        selected = selected == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(shelf.name) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                ProductsPane(householdId = householdId, shelfId = state.shelves[page].id)
            }
        }
    }
}
