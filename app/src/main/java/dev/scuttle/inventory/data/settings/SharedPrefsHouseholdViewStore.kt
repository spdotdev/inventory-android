package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsHouseholdViewStore(
    context: Context,
) : HouseholdViewStore {
    private val prefs = context.getSharedPreferences("household_view", Context.MODE_PRIVATE)

    override fun collapsed(): Set<Long> =
        prefs.getStringSet(KEY_COLLAPSED, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    override fun toggleCollapsed(id: Long) {
        val current = collapsed().toMutableSet()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putStringSet(KEY_COLLAPSED, current.map { it.toString() }.toSet()).apply()
    }

    // A StringSet can't preserve order, so the drag order is stored as a single
    // delimited string instead — same prefs file, a different key/shape than
    // `collapsed()`'s set.
    override fun order(): List<Long> =
        prefs.getString(KEY_ORDER, null)?.takeIf { it.isNotEmpty() }?.split(ORDER_DELIMITER)?.mapNotNull {
            it.toLongOrNull()
        } ?: emptyList()

    override fun setOrder(ids: List<Long>) {
        prefs.edit().putString(KEY_ORDER, ids.joinToString(ORDER_DELIMITER)).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_COLLAPSED = "collapsed_household_ids"
        const val KEY_ORDER = "household_order"
        const val ORDER_DELIMITER = ","
    }
}
