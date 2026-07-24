package dev.scuttle.inventory

import dev.scuttle.inventory.ui.appupdate.UpdateInstaller
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateInstallerTest {
    @Test
    fun `requireHttps accepts an https url`() {
        UpdateInstaller.Companion.requireHttps("https://example.com/update.apk")
    }

    @Test
    fun `requireHttps accepts https regardless of case`() {
        UpdateInstaller.Companion.requireHttps("HTTPS://example.com/update.apk")
    }

    @Test
    fun `requireHttps rejects a plain http url`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                UpdateInstaller.Companion.requireHttps("http://example.com/update.apk")
            }
        assert(error.message?.contains("https") == true)
    }

    @Test
    fun `requireHttps rejects other schemes`() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateInstaller.Companion.requireHttps("ftp://example.com/update.apk")
        }
    }
}
