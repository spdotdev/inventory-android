package dev.scuttle.inventory.data.settings

/** Whether the shelves screen shows tabs+products or a plain shelf list. Global, not per-location. */
interface ShelfViewStore {
    fun isListView(): Boolean

    fun setListView(listView: Boolean)

    /** Forget the preference so one account's choice never carries into the next session. */
    fun clear() {}
}
