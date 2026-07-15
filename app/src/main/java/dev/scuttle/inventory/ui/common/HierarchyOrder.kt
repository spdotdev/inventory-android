package dev.scuttle.inventory.ui.common

/**
 * The one ordering rule for every hierarchy list in the app.
 *
 * 1. Manual position — what the user set by moving rows. It always wins.
 * 2. Name — the tie-break for items nobody has reordered yet (they all sit at 0).
 *
 * A star is deliberately NOT an input here. Starring is a marker and a filter,
 * never a sort: shelf order is physical, and a star that floated the bottom shelf
 * to the top of the list would leave the app disagreeing with the actual fridge.
 * Keeping favourites out of this signature makes that impossible to get wrong.
 */
fun <T> orderByPosition(
    items: List<T>,
    position: (T) -> Int?,
    name: (T) -> String,
): List<T> =
    items.sortedWith(
        compareBy<T> { position(it) ?: 0 }
            .thenBy { name(it).lowercase() },
    )
