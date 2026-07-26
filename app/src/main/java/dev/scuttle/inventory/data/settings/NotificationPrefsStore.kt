package dev.scuttle.inventory.data.settings

interface NotificationPrefsStore {
    fun get(): NotificationPrefs

    fun set(prefs: NotificationPrefs)
}
