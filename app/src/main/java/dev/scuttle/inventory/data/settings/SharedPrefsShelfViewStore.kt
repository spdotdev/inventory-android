package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsShelfViewStore(
    context: Context,
) : ShelfViewStore {
    private val prefs = context.getSharedPreferences("inventory_settings", Context.MODE_PRIVATE)

    override fun isListView(): Boolean = prefs.getBoolean(KEY_LIST_VIEW, false)

    override fun setListView(listView: Boolean) {
        prefs.edit().putBoolean(KEY_LIST_VIEW, listView).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_LIST_VIEW).apply()
    }

    private companion object {
        const val KEY_LIST_VIEW = "shelf_list_view"
    }
}
