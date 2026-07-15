package dev.scuttle.inventory.data.settings

/**
 * Per-device view state for the storage list's household groups: which are
 * collapsed, and the order the user has dragged them into.
 *
 * A household's *physical* structure (its locations/shelves/products) is
 * shared, server-owned reality. Which of a user's households they currently
 * care about looking at, and in what order, is a personal view preference —
 * it never reaches the server (D8). Mirrors [ShelfViewStore]'s shape.
 */
interface HouseholdViewStore {
    fun collapsed(): Set<Long>

    fun toggleCollapsed(id: Long)

    fun order(): List<Long>

    fun setOrder(ids: List<Long>)

    /** Forget collapse state + order so one account's view never carries into the next session. */
    fun clear() {}
}
