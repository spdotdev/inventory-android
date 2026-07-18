package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsHintsStore(
    context: Context,
) : HintsStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun hasSeen(hintId: String): Boolean = prefs.getBoolean(keyFor(hintId), false)

    override fun markSeen(hintId: String) {
        prefs.edit().putBoolean(keyFor(hintId), true).apply()
    }

    private fun keyFor(hintId: String) = "hint_seen_$hintId"
}
