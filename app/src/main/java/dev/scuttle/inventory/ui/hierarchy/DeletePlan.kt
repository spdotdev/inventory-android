package dev.scuttle.inventory.ui.hierarchy

/**
 * What one delete gesture is about to destroy, and therefore what the user has
 * to be asked.
 *
 * A BATCH gets ONE dialog, not one per item: selecting three shelves and being
 * interrogated three times is worse than being told "3 shelves · 17 products"
 * once and choosing once.
 */
data class DeletePlan(
    /** How many containers (shelves or locations) are selected. */
    val itemCount: Int,
    /** How many products live inside them, in total. Feeds the summary line. */
    val productCount: Int,
    /**
     * What the SERVER counts as "has contents" for the thing being deleted.
     *
     * These two are NOT the same rule, and getting it wrong 422s every delete:
     *  - deleting a SHELF    → the server asks when the shelf has PRODUCTS.
     *  - deleting a LOCATION → the server asks when the location has SHELVES
     *                          (`shelf_count`), even if those shelves are empty.
     *
     * So the caller passes the right count: `product_count` for shelves,
     * `shelf_count` for locations. Do not "simplify" this to productCount.
     */
    val contentCount: Int,
) {
    /** An empty container is safe to delete outright — a plain confirm will do. */
    val needsStrategy: Boolean get() = contentCount > 0
}

// Deliberately no `canMove`/`hasOtherTargets` here. Whether "move the contents
// elsewhere" can be offered is decided from the target list the caller actually
// hands the dialog (`targets.isNotEmpty()`), not from a boolean passed alongside
// it. One signal, and it is the one the user can see: a plan claiming canMove
// while the rendered target list is empty would offer a move with nowhere to go.
