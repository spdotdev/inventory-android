package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.settings.NotificationPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckWorkerTest {
    @Test
    fun disabledPrefSkipsCheck() {
        assertFalse(shouldCheckForUpdates(NotificationPrefs(appUpdatesEnabled = false)))
    }

    @Test
    fun defaultChecks() {
        assertTrue(shouldCheckForUpdates(NotificationPrefs()))
    }
}
