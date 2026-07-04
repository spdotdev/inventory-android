package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.common.sortedByOrder
import dev.scuttle.inventory.ui.products.applyView
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure filter + sort logic backing the products list and search results. */
class ProductViewTest {

    private val products = listOf(
        ProductDto(id = 1, name = "Peas", quantity = 4, shelf_id = 1, code = "P-100"),
        ProductDto(id = 2, name = "apples", quantity = 0, shelf_id = 1, is_mandatory = true),
        ProductDto(id = 3, name = "Butter", quantity = 2, shelf_id = 1, is_mandatory = true),
        ProductDto(id = 4, name = "milk", quantity = 0, shelf_id = 1),
    )

    @Test
    fun name_sort_is_case_insensitive() {
        val names = products.applyView("", false, false, SortOrder.NAME_ASC).map { it.name }
        assertEquals(listOf("apples", "Butter", "milk", "Peas"), names)
    }

    @Test
    fun name_desc_reverses_the_case_insensitive_order() {
        val names = products.applyView("", false, false, SortOrder.NAME_DESC).map { it.name }
        assertEquals(listOf("Peas", "milk", "Butter", "apples"), names)
    }

    @Test
    fun quantity_sorts_break_ties_by_name() {
        val desc = products.applyView("", false, false, SortOrder.QUANTITY_DESC).map { it.name }
        assertEquals(listOf("Peas", "Butter", "apples", "milk"), desc)

        val asc = products.applyView("", false, false, SortOrder.QUANTITY_ASC).map { it.name }
        // Two zero-qty items (apples, milk) tie and fall back to name order.
        assertEquals(listOf("apples", "milk", "Butter", "Peas"), asc)
    }

    @Test
    fun query_matches_name_or_code_case_insensitively() {
        assertEquals(listOf(2L), products.applyView("APP", false, false, SortOrder.NAME_ASC).map { it.id })
        // Matches the code P-100 on "Peas".
        assertEquals(listOf(1L), products.applyView("p-100", false, false, SortOrder.NAME_ASC).map { it.id })
    }

    @Test
    fun mandatory_only_and_out_of_stock_flags_narrow_the_list() {
        assertEquals(
            setOf(2L, 3L),
            products.applyView("", mandatoryOnly = true, outOfStockOnly = false, SortOrder.NAME_ASC).map { it.id }.toSet(),
        )
        assertEquals(
            setOf(2L, 4L),
            products.applyView("", mandatoryOnly = false, outOfStockOnly = true, SortOrder.NAME_ASC).map { it.id }.toSet(),
        )
        // Combined: mandatory AND out of stock → only apples.
        assertEquals(
            listOf(2L),
            products.applyView("", mandatoryOnly = true, outOfStockOnly = true, SortOrder.NAME_ASC).map { it.id },
        )
    }

    @Test
    fun sorted_by_order_is_a_reusable_helper() {
        val words = listOf("banana" to 1, "Apple" to 3, "cherry" to 2)
        val byQtyDesc = words.sortedByOrder(SortOrder.QUANTITY_DESC, name = { it.first }, quantity = { it.second })
        assertEquals(listOf("Apple", "cherry", "banana"), byQtyDesc.map { it.first })
    }
}
