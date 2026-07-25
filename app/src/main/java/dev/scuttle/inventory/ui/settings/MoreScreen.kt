package dev.scuttle.inventory.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.theme.FrostCard

const val SETTINGS_VERSION_TEST_TAG = "settings-version"

/**
 * The "More" tab root: a hub of square category buttons rather than one long
 * scrolling list — Settings had grown to cram language/theme/reminders/join/
 * households/account into a single screen with no structure. Each button opens
 * its own focused screen; nothing here mutates state directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenHouseholds: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
) {
    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.settings_title), modifier = Modifier.semantics { heading() }) },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CategoryButton(
                        label = stringResource(R.string.settings_hub_notifications_button),
                        icon = Icons.Default.Notifications,
                        onClick = onOpenNotifications,
                        modifier = Modifier.weight(1f),
                    )
                    CategoryButton(
                        label = stringResource(R.string.settings_hub_households_button),
                        icon = Icons.Default.Groups,
                        onClick = onOpenHouseholds,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CategoryButton(
                        label = stringResource(R.string.settings_hub_account_button),
                        icon = Icons.Default.AccountCircle,
                        onClick = onOpenAccount,
                        modifier = Modifier.weight(1f),
                    )
                    CategoryButton(
                        label = stringResource(R.string.settings_hub_general_button),
                        icon = Icons.Default.Tune,
                        onClick = onOpenGeneral,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Testers are asked to quote this in bug reports (see the GitHub issue
            // forms) — without it they can only guess which build they're on.
            Text(
                text =
                    stringResource(
                        R.string.settings_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 16.dp)
                        .testTag(SETTINGS_VERSION_TEST_TAG),
            )
        }
    }
}

/** A squircle-shaped category button — generously rounded, always square via aspectRatio(1f). */
@Composable
private fun CategoryButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val iconSize = 32.dp
