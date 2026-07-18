package dev.scuttle.inventory

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BUG-2 regression guard: Auto Backup must never restore `inventory_secure_prefs`
 * to another device. Its AES master key lives in the hardware Keystore and is not
 * backed up, so a restored copy can't be decrypted — EncryptedSharedPreferences
 * then throws during Hilt graph construction, a launch crash-loop. Both rule files
 * are needed: dataExtractionRules (API 31+) and fullBackupContent (API 26-30).
 */
class BackupRulesTest {
    private fun appRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/AndroidManifest.xml").exists()) return dir
            if (File(dir, "app/src/main/AndroidManifest.xml").exists()) return File(dir, "app")
            dir = dir.parentFile
        }
        error("Could not locate the app module from ${File(".").absolutePath}")
    }

    @Test
    fun `manifest declares backup rules excluding the encrypted prefs`() {
        val root = appRoot()
        val manifest = File(root, "src/main/AndroidManifest.xml").readText()
        assertTrue(
            "manifest must reference dataExtractionRules",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
        assertTrue(
            "manifest must reference fullBackupContent for API 26-30",
            manifest.contains("android:fullBackupContent=\"@xml/full_backup_content\""),
        )

        val extraction = File(root, "src/main/res/xml/data_extraction_rules.xml").readText()
        val fullBackup = File(root, "src/main/res/xml/full_backup_content.xml").readText()
        for ((name, xml) in mapOf("data_extraction_rules" to extraction, "full_backup_content" to fullBackup)) {
            assertTrue(
                "$name must exclude inventory_secure_prefs from sharedpref backup",
                Regex(
                    """<exclude\s+domain="sharedpref"\s+path="inventory_secure_prefs\.xml"\s*/>""",
                ).containsMatchIn(xml),
            )
        }
    }
}
