package dev.scuttle.inventory

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ALSO FIX (final review): `shelf_unsorted`, `delete_undone` and `delete_undo_failed`
 * each shipped as a string resource and then never got wired to the requirement it
 * belonged to — an unused string breaks no gate, so nothing noticed until a reviewer
 * went looking by hand. This is the cheap check that would have caught all three for
 * free: every string declared in `values/strings.xml` must have at least one reference
 * somewhere in this module's own Kotlin or XML source (a Kotlin `R.string.<name>` or an
 * XML `@string/<name>`).
 *
 * Deliberately a plain source scan, not a Compose/ViewModel test — mirrors
 * NavigationWiringTest's own reasoning: it's the cheapest check that still
 * discriminates, and works without Robolectric/compose-ui-test on this module's JVM
 * classpath.
 */
class StringResourceUsageTest {
    private val appRoot: File by lazy { findAppModuleRoot() }

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

    /**
     * Names referenced only dynamically (assembled from a variable at runtime rather
     * than written as a literal `R.string.<name>` / `@string/<name>` anywhere), so a
     * text scan can never find them — empty today. Keep it that way unless you can
     * point to the exact dynamic-construction call site a name comes from; this
     * allowlist is exactly the kind of thing that quietly hides the next dropped
     * requirement if it's used as a shortcut instead.
     */
    private val allowlist = emptySet<String>()

    private fun definedStringNames(): List<String> {
        val stringsXml = File(appRoot, "src/main/res/values/strings.xml")
        check(stringsXml.exists()) { "Expected ${stringsXml.absolutePath} to exist" }
        return Regex("""<string name="([^"]+)">""")
            .findAll(stringsXml.readText())
            .map { it.groupValues[1] }
            .toList()
    }

    /** Every .kt/.xml file under src, EXCLUDING strings.xml itself — its own
     * `<string name="...">` declaration line is not a USE of the resource, and would
     * trivially "reference" every single name if left in. */
    private fun allSourceText(): String =
        File(appRoot, "src")
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            .filterNot { it.name == "strings.xml" }
            .joinToString("\n") { it.readText() }

    @Test
    fun `every declared string resource has at least one reference`() {
        val haystack = allSourceText()
        val unreferenced =
            definedStringNames().filterNot { name ->
                val referencePattern = Regex("""(R\.string\.|@string/)${Regex.escape(name)}\b""")
                name in allowlist || referencePattern.containsMatchIn(haystack)
            }
        assertTrue(
            "These string resources are declared in values/strings.xml but never referenced from " +
                "any Kotlin or XML source (R.string.<name> / @string/<name>) — an unused string " +
                "breaks no gate, so it silently ships a dropped requirement: $unreferenced",
            unreferenced.isEmpty(),
        )
    }
}
