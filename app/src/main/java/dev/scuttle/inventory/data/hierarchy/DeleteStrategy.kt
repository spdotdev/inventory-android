package dev.scuttle.inventory.data.hierarchy

/**
 * What to do with a shelf's products when the shelf is deleted. The server
 * refuses to guess — a non-empty shelf deleted with no strategy is a 422.
 */
enum class ShelfDeleteStrategy(
    val wire: String,
) {
    MOVE_PRODUCTS("move_products"),
    UNSORT_PRODUCTS("unsort_products"),
    DELETE_PRODUCTS("delete_products"),
}

/**
 * What to do with a location's contents. There is deliberately no `unsort` here:
 * "unsorted" means off-shelf but still IN this location, and the location is the
 * thing being deleted.
 */
enum class LocationDeleteStrategy(
    val wire: String,
) {
    MOVE_CONTENTS("move_contents"),
    DELETE_CONTENTS("delete_contents"),
}
