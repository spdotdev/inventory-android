package dev.scuttle.inventory.ui.products

import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.common.sortedByOrder

/**
 * Apply the user's local filter + sort to a shelf's products. Pure and
 * server-agnostic: [query] matches name or code (case-insensitive), and the
 * two flags narrow to mandatory items or out-of-stock items respectively.
 * Filtering runs before sorting so the ordering applies to the visible subset.
 */
fun List<ProductDto>.applyView(
    query: String,
    mandatoryOnly: Boolean,
    outOfStockOnly: Boolean,
    sort: SortOrder,
): List<ProductDto> {
    val q = query.trim()
    return this
        .filter { p ->
            val codeMatches = p.code?.contains(q, ignoreCase = true) == true
            (q.isEmpty() || p.name.contains(q, ignoreCase = true) || codeMatches) &&
                (!mandatoryOnly || p.is_mandatory == true) &&
                (!outOfStockOnly || p.quantity == 0)
        }
        .sortedByOrder(sort, name = { it.name }, quantity = { it.quantity })
}
