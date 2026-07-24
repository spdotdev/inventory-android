package dev.scuttle.inventory.data.settings

data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
)
