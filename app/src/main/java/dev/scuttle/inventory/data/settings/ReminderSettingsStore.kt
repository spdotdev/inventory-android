package dev.scuttle.inventory.data.settings

interface ReminderSettingsStore {
    fun get(): ReminderSettings

    fun set(settings: ReminderSettings)
}
