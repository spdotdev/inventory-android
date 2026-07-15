package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.ui.common.orderByPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class HierarchyOrderTest {
    private fun shelf(
        id: Long,
        name: String,
        position: Int?,
        isSystem: Boolean = false,
    ) = ShelfDto(id = id, name = name, position = position, location_id = 1L, is_system = isSystem)

    private fun order(items: List<ShelfDto>) = orderByPosition(items, { it.position }, { it.name })

    @Test
    fun manual_position_wins() {
        val items =
            listOf(
                shelf(1, "Top", 0),
                shelf(2, "Middle", 1),
                shelf(3, "Bottom", 2),
            )

        assertEquals(listOf(1L, 2L, 3L), order(items.shuffled()).map { it.id })
    }

    @Test
    fun a_star_never_reorders_anything() {
        // THE rule. Shelf order is physical: if starring the bottom shelf floated
        // it to the top, the list would stop matching the fridge the user is
        // standing in front of. A star is a marker and a filter, never a sort —
        // so this function does not even take a favourites set as an argument.
        val items =
            listOf(
                shelf(1, "Top", 0),
                shelf(2, "Middle", 1),
                shelf(3, "Bottom", 2),
            )

        // Whatever the caller has starred, the order is unchanged.
        assertEquals(listOf(1L, 2L, 3L), order(items).map { it.id })
    }

    @Test
    fun name_is_only_the_tie_break_for_never_reordered_items() {
        // Everything sits at position 0 until someone drags. Falling back to name
        // keeps a fresh list stable instead of ordering it by insertion id.
        val items =
            listOf(
                shelf(1, "Zebra", 0),
                shelf(2, "Apple", 0),
                shelf(3, "Mango", 0),
            )

        assertEquals(listOf("Apple", "Mango", "Zebra"), order(items).map { it.name })
    }

    @Test
    fun a_null_position_sorts_as_zero() {
        // An older server, or a row created before the position column existed.
        val items =
            listOf(
                shelf(1, "Bbb", null),
                shelf(2, "Aaa", null),
                shelf(3, "Ccc", 1),
            )

        assertEquals(listOf("Aaa", "Bbb", "Ccc"), order(items).map { it.name })
    }

    @Test
    fun the_sort_is_stable_across_repeated_calls() {
        val items = listOf(shelf(1, "Same", 0), shelf(2, "Same", 0))

        assertEquals(order(items).map { it.id }, order(order(items)).map { it.id })
    }
}
