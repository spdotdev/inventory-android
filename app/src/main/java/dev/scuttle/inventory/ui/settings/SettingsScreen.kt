package dev.scuttle.inventory.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.scuttle.inventory.BuildConfig
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.settings.AppLanguage
import dev.scuttle.inventory.ui.common.LiveStatusText
import dev.scuttle.inventory.ui.theme.ThemeMode

const val SETTINGS_VERSION_TEST_TAG = "settings-version"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onOpenHouseholds: () -> Unit = {},
    themeViewModel: ThemeViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
    joinViewModel: JoinHouseholdViewModel = hiltViewModel(),
) {
    val mode by themeViewModel.mode.collectAsState()
    val language by languageViewModel.language.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    val joinState by joinViewModel.state.collectAsState()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val scanPrompt = stringResource(R.string.settings_scan_prompt)

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { joinViewModel.onCodeScanned(it) }
        }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_language_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = {
                            languageViewModel.setLanguage(option)
                            activity?.recreate()
                        },
                        label = { Text(option.label) },
                    )
                }
            }

            Text(text = stringResource(R.string.settings_theme_section), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { themeViewModel.setMode(option) },
                        label = {
                            Text(
                                when (option) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                },
                            )
                        },
                    )
                }
            }

            Text(text = stringResource(R.string.settings_join_section), style = MaterialTheme.typography.titleMedium)
            joinState.error?.let {
                LiveStatusText(it)
            }
            if (joinState.success) {
                val successMessage =
                    if (joinState.joinedRole == "member") {
                        stringResource(R.string.settings_join_success) + " " +
                            stringResource(R.string.settings_join_success_member_hint)
                    } else {
                        stringResource(R.string.settings_join_success)
                    }
                LiveStatusText(
                    successMessage,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = joinState.code,
                    onValueChange = joinViewModel::onCodeChange,
                    label = { Text(stringResource(R.string.settings_join_field)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(onDone = {
                            keyboardController?.hide()
                            joinViewModel.join()
                        }),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        keyboardController?.hide()
                        joinViewModel.join()
                    },
                    enabled = !joinState.loading && joinState.code.isNotBlank(),
                ) {
                    Text(stringResource(R.string.settings_join_button))
                }
            }
            Button(
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setPrompt(scanPrompt)
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_scan_qr_button))
            }

            Text(
                text = stringResource(R.string.settings_households_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = onOpenHouseholds,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_my_households_button))
            }

            Text(text = stringResource(R.string.settings_account_section), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_sign_out_button))
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
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag(SETTINGS_VERSION_TEST_TAG),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_dialog_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_dialog_text)) },
            confirmButton = {
                TextButton(onClick = onSignOut) {
                    Text(stringResource(R.string.settings_sign_out_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
