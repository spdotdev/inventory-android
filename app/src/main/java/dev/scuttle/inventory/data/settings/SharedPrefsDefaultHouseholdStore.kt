package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsDefaultHouseholdStore(
    context: Context,
) : DefaultHouseholdStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun get(): Long? = prefs.getLong(KEY, -1L).takeIf { it != -1L }

    override fun set(householdId: Long) {
        prefs.edit().putLong(KEY, householdId).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "default_household"
    }
}
