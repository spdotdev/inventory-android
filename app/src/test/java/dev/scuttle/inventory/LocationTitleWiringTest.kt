package dev.scuttle.inventory

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ALSO FIX (final review): LocationDetailScreen's top bar used to always render the
 * generic "Shelves" title (R.string.location_shelves_title), even though the location
 * itself became renamable this branch — the spec requires the location's actual name.
 * Not expressible as a normal Compose/ViewModel unit test (no Robolectric/compose-ui-test
 * on this module's JVM classpath — see NavigationWiringTest's own doc), so this is the
 * same cheapest-check-that-still-discriminates source scan: it fails if the title stops
 * rendering the location's own name.
 */
class LocationTitleWiringTest {
    private fun read(relativePath: String): String {
        val file = File(findAppModuleRoot(), "src/main/java/dev/scuttle/inventory/$relativePath")
        check(file.exists()) { "Expected source file not found: ${file.absolutePath}" }
        return file.readText()
    }

    private fun findAppModuleRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/java/dev/scuttle/inventory/MainActivity.kt").exists()) return dir
            if (File(dir, "app/src/main/java/dev/scuttle/inventory/MainActivity.kt").exists()) {
                return File(dir, "app")
            }
            dir = dir.parentFile
        }
        error("Could not locate the app module's src/main from working dir ${File(".").absolutePath}")
    }

    @Test
    fun `LocationDetailScreen's top bar title renders the location's own name`() {
        val src = read("ui/location/LocationDetailScreen.kt")

        assertTrue(
            "LocationDetailScreen no longer derives a locationName from drawerViewModel's " +
                "state — the top bar would have nothing but the generic title to fall back to.",
            src.contains("val locationName ="),
        )
        assertTrue(
            "LocationDetailScreen's top bar title no longer renders locationName — it " +
                "regressed to always showing the generic \"Shelves\" title even though the " +
                "location is renamable.",
            src.contains("Text(locationName ?: stringResource(R.string.location_shelves_title))"),
        )
    }
}
