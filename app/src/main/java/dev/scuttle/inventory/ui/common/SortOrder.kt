package dev.scuttle.inventory.ui.common

import dev.scuttle.inventory.R

/**
 * Client-side ordering shared by the products list and search results. The
 * server returns rows in insertion order; these let the user re-sort locally.
 * It is a transient *view* preference (always-online, nothing persisted), not
 * part of the server-authoritative model.
 */
enum class SortOrder(
    val labelRes: Int,
) {
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    QUANTITY_DESC(R.string.sort_quantity_desc),
    QUANTITY_ASC(R.string.sort_quantity_asc),
}

/**
 * Order a list by [order], reading each item's display name and quantity via
 * the given selectors. Name comparison is case-insensitive; quantity sorts
 * break ties by name so the ordering is stable and predictable.
 */
fun <T> List<T>.sortedByOrder(
    order: SortOrder,
    name: (T) -> String,
    quantity: (T) -> Int,
): List<T> {
    val byName: Comparator<T> = compareBy(String.CASE_INSENSITIVE_ORDER, name)
    val comparator =
        when (order) {
            SortOrder.NAME_ASC -> byName
            SortOrder.NAME_DESC -> byName.reversed()
            SortOrder.QUANTITY_DESC -> compareByDescending(quantity).then(byName)
            SortOrder.QUANTITY_ASC -> compareBy(quantity).then(byName)
        }
    return sortedWith(comparator)
}
