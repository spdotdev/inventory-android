package dev.scuttle.inventory.ui.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.settings.ReminderSettings
import java.text.DateFormatSymbols
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    reminderViewModel: ReminderViewModel = hiltViewModel(),
    lowStockReminderViewModel: LowStockReminderViewModel = hiltViewModel(),
    notificationPrefsViewModel: NotificationPrefsViewModel = hiltViewModel(),
) {
    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        stringResource(R.string.settings_notifications_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
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
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MissingItemsSection(reminderViewModel)
            LowStockSection(lowStockReminderViewModel)
            HouseholdNotificationsSection(notificationPrefsViewModel)
            WeeklySummarySection(notificationPrefsViewModel)
            AppUpdatesSection(notificationPrefsViewModel)
        }
    }
}

@Composable
private fun SectionHeading(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ToggleRow(
    labelRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(labelRes))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val timePickerState =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text(stringResource(R.string.settings_missing_items_reminder_time_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_missing_items_reminder_time_cancel))
            }
        },
        text = { TimePicker(state = timePickerState) },
    )
}

@Composable
private fun MissingItemsSection(reminderViewModel: ReminderViewModel) {
    val reminderSettings by reminderViewModel.settings.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(R.string.settings_missing_items_reminder_section)
        ToggleRow(
            labelRes = R.string.settings_missing_items_reminder_toggle,
            checked = reminderSettings.enabled,
            onCheckedChange = { reminderViewModel.setEnabled(it) },
        )
        if (reminderSettings.enabled) {
            TextButton(onClick = { showTimePicker = true }) {
                Text(
                    stringResource(
                        R.string.settings_missing_items_reminder_time,
                        reminderSettings.hour,
                        reminderSettings.minute,
                    ),
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = reminderSettings.hour,
            initialMinute = reminderSettings.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                reminderViewModel.setTime(hour, minute)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun LowStockSection(viewModel: LowStockReminderViewModel) {
    val settings: ReminderSettings by viewModel.settings.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(R.string.settings_low_stock_section)
        ToggleRow(
            labelRes = R.string.settings_low_stock_toggle,
            checked = settings.enabled,
            onCheckedChange = { viewModel.setEnabled(it) },
        )
        if (settings.enabled) {
            TextButton(onClick = { showTimePicker = true }) {
                Text(stringResource(R.string.settings_low_stock_time, settings.hour, settings.minute))
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = settings.hour,
            initialMinute = settings.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setTime(hour, minute)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun HouseholdNotificationsSection(viewModel: NotificationPrefsViewModel) {
    val prefs by viewModel.prefs.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(R.string.settings_household_notifications_section)
        ToggleRow(
            labelRes = R.string.settings_household_events_toggle,
            checked = prefs.householdEventsEnabled,
            onCheckedChange = { viewModel.setHouseholdEvents(it) },
        )
        ToggleRow(
            labelRes = R.string.settings_activity_digest_toggle,
            checked = prefs.activityDigestEnabled,
            onCheckedChange = { viewModel.setActivityDigest(it) },
        )
    }
}

@Composable
private fun WeekdayPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (dayOfWeek: Int) -> Unit,
) {
    // DateFormatSymbols().weekdays is 1-indexed by Calendar.SUNDAY(1)..Calendar.SATURDAY(7);
    // index 0 is unused.
    val weekdays = remember { DateFormatSymbols().weekdays }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column {
                for (dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
                    TextButton(onClick = { onSelect(dayOfWeek) }) {
                        Text(weekdays[dayOfWeek])
                    }
                }
            }
        },
    )
}

@Composable
private fun WeeklySummarySection(viewModel: NotificationPrefsViewModel) {
    val prefs by viewModel.prefs.collectAsState()
    var showDayPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val weekdays = remember { DateFormatSymbols().weekdays }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(R.string.settings_weekly_summary_section)
        ToggleRow(
            labelRes = R.string.settings_weekly_summary_toggle,
            checked = prefs.weeklySummaryEnabled,
            onCheckedChange = { viewModel.setWeeklySummaryEnabled(it) },
        )
        if (prefs.weeklySummaryEnabled) {
            TextButton(onClick = { showDayPicker = true }) {
                Text(stringResource(R.string.settings_weekly_summary_day, weekdays[prefs.weeklyDayOfWeek]))
            }
            TextButton(onClick = { showTimePicker = true }) {
                Text(stringResource(R.string.settings_weekly_summary_time, prefs.weeklyHour, prefs.weeklyMinute))
            }
        }
    }

    if (showDayPicker) {
        WeekdayPickerDialog(
            onDismiss = { showDayPicker = false },
            onSelect = { dayOfWeek ->
                viewModel.setWeeklyDay(dayOfWeek)
                showDayPicker = false
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = prefs.weeklyHour,
            initialMinute = prefs.weeklyMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setWeeklyTime(hour, minute)
                showTimePicker = false
            },
        )
    }
}

@Composable
private fun AppUpdatesSection(viewModel: NotificationPrefsViewModel) {
    val prefs by viewModel.prefs.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(R.string.settings_app_updates_section)
        ToggleRow(
            labelRes = R.string.settings_app_updates_toggle,
            checked = prefs.appUpdatesEnabled,
            onCheckedChange = { viewModel.setAppUpdates(it) },
        )
    }
}
