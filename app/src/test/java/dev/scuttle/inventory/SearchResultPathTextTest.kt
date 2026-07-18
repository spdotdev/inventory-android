package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.SearchResultDto
import dev.scuttle.inventory.ui.search.searchResultPathText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * H4: [searchResultPathText] picks the localized "Unsorted" shelf label instead of the server's
 * raw shelf name whenever `shelf_is_system` is true, rebuilding the path client-side rather than
 * trying to localize a substring of the server's pre-built `path`.
 */
class SearchResultPathTextTest {
    private fun result(
        location: String = "Kitchen",
        shelf: String = "Fridge",
        path: String = "Kitchen › Fridge",
        shelfIsSystem: Boolean = false,
    ) = SearchResultDto(
        id = 1,
        name = "Milk",
        quantity = 2,
        location = location,
        shelf = shelf,
        path = path,
        shelf_is_system = shelfIsSystem,
    )

    @Test
    fun `a non-system shelf uses the server-provided path`() {
        val text =
            searchResultPathText(
                result = result(path = "Kitchen › Fridge"),
                unsortedShelfLabel = "Unsorted",
                locationUnavailableLabel = "Location unavailable",
            )

        assertEquals("Kitchen › Fridge", text)
    }

    @Test
    fun `a system shelf uses the localized unsorted label instead of the server's shelf name`() {
        val text =
            searchResultPathText(
                result = result(location = "Kitchen", shelf = "Unsorted", path = "Kitchen › Unsorted", shelfIsSystem = true),
                unsortedShelfLabel = "Ongesorteerd",
                locationUnavailableLabel = "Locatie niet beschikbaar",
            )

        assertEquals("Kitchen › Ongesorteerd", text)
    }

    @Test
    fun `a system shelf with no location shows just the localized label`() {
        val text =
            searchResultPathText(
                result = result(location = "", shelf = "Unsorted", path = "", shelfIsSystem = true),
                unsortedShelfLabel = "Unsorted",
                locationUnavailableLabel = "Location unavailable",
            )

        assertEquals("Unsorted", text)
    }

    @Test
    fun `a blank path with no location or shelf falls back to the unavailable label`() {
        val text =
            searchResultPathText(
                result = result(location = "", shelf = "", path = ""),
                unsortedShelfLabel = "Unsorted",
                locationUnavailableLabel = "Location unavailable",
            )

        assertEquals("Location unavailable", text)
    }

    @Test
    fun `a blank path with location and shelf present rebuilds from the parts`() {
        val text =
            searchResultPathText(
                result = result(location = "Kitchen", shelf = "Fridge", path = ""),
                unsortedShelfLabel = "Unsorted",
                locationUnavailableLabel = "Location unavailable",
            )

        assertEquals("Kitchen › Fridge", text)
    }
}
