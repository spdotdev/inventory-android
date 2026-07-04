package dev.scuttle.inventory.data.settings

interface FavoritesStore {
    fun getFavoriteLocations(): Set<Long>
    fun toggleFavoriteLocation(id: Long)
    fun isFavoriteLocation(id: Long): Boolean
    fun getFavoriteShelves(): Set<Long>
    fun toggleFavoriteShelf(id: Long)
    fun isFavoriteShelf(id: Long): Boolean

    /** Forget all favorites so one account's picks never carry into the next session. */
    fun clear() {}
}
