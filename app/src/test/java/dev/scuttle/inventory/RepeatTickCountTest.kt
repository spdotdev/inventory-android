package dev.scuttle.inventory

import dev.scuttle.inventory.ui.common.repeatTickCount
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * GAP-5 M10: the quantity stepper's press-and-hold repeat schedule. Pure logic, no Compose —
 * see `ui/common/RepeatingIconButton.kt` for the reasoning behind accumulating locally and
 * sending one network call on release instead of one per tick.
 */
class RepeatTickCountTest {
    @Test
    fun `a release before the initial delay still counts as one tap`() {
        assertEquals(1, repeatTickCount(heldMillis = 0, initialDelayMillis = 400, repeatIntervalMillis = 250))
        assertEquals(1, repeatTickCount(heldMillis = 399, initialDelayMillis = 400, repeatIntervalMillis = 250))
    }

    @Test
    fun `repeating starts exactly at the initial delay`() {
        assertEquals(2, repeatTickCount(heldMillis = 400, initialDelayMillis = 400, repeatIntervalMillis = 250))
    }

    @Test
    fun `ticks accrue at the repeat interval after the initial delay`() {
        // tick schedule: t=0, 400, 650, 900, 1150 -> 5 ticks have fired by 1150ms held
        assertEquals(5, repeatTickCount(heldMillis = 1150, initialDelayMillis = 400, repeatIntervalMillis = 250))
        // one millisecond short of the 5th tick still only counts 4
        assertEquals(4, repeatTickCount(heldMillis = 1149, initialDelayMillis = 400, repeatIntervalMillis = 250))
    }

    @Test
    fun `throttled to well under the naive 10 per second rate`() {
        // Held for 2 full seconds: at a naive 10/sec this would be ~20 network calls;
        // the throttled 4/sec (250ms interval) schedule caps it well below that.
        val ticks = repeatTickCount(heldMillis = 2000, initialDelayMillis = 400, repeatIntervalMillis = 250)
        assert(ticks < 10) { "expected throttled tick count under 10, got $ticks" }
    }

    @Test
    fun `negative hold duration is treated as no ticks`() {
        assertEquals(0, repeatTickCount(heldMillis = -1, initialDelayMillis = 400, repeatIntervalMillis = 250))
    }
}
