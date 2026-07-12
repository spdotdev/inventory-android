package dev.scuttle.inventory.data.settings

import android.content.Context

class SharedPrefsFavoritesStore(
    context: Context,
) : FavoritesStore {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    override fun getFavoriteLocations(): Set<Long> = getLongSet("locations")

    override fun isFavoriteLocation(id: Long): Boolean = id in getFavoriteLocations()

    override fun toggleFavoriteLocation(id: Long) = toggleInSet("locations", id)

    override fun getFavoriteShelves(): Set<Long> = getLongSet("shelves")

    override fun isFavoriteShelf(id: Long): Boolean = id in getFavoriteShelves()

    override fun toggleFavoriteShelf(id: Long) = toggleInSet("shelves", id)

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun getLongSet(key: String): Set<Long> =
        prefs.getStringSet(key, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    private fun toggleInSet(
        key: String,
        id: Long,
    ) {
        val current = getLongSet(key).toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        prefs.edit().putStringSet(key, current.map { it.toString() }.toSet()).apply()
    }
}
