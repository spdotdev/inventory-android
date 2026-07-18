package dev.scuttle.inventory

import dev.scuttle.inventory.ui.common.RepeatTiming
import dev.scuttle.inventory.ui.common.awaitHoldAndFlush
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H1: `onRelease` must fire with the accumulated tick count whether the hold ends normally
 * (pointer up) or the gesture's coroutine is cancelled out from under it (navigate away,
 * backgrounding, config change). See `ui/common/RepeatingIconButton.kt`'s `awaitHoldAndFlush`
 * doc comment for why this is safe to do from `finally` without `NonCancellable`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatingHoldFlushTest {
    @Test
    fun `onRelease fires once with the accumulated ticks on normal release`() =
        runTest {
            var releasedTicks = -1
            var releaseCount = 0
            val released = CompletableDeferred<Unit>()

            val job =
                launch {
                    awaitHoldAndFlush(
                        onTick = {},
                        onRelease = { ticks ->
                            releasedTicks = ticks
                            releaseCount++
                        },
                        timing = RepeatTiming(initialDelayMillis = 400, repeatIntervalMillis = 250),
                        awaitRelease = { released.await() },
                    )
                }

            // t=0 tick, then repeats fire at 400, 650, 900 -> 4 ticks by the time we release.
            advanceTimeBy(900)
            released.complete(Unit)
            job.join()

            assertEquals(4, releasedTicks)
            assertEquals(1, releaseCount)
        }

    @Test
    fun `onRelease still fires with accumulated ticks when the gesture is cancelled`() =
        runTest {
            var releasedTicks = -1
            var releaseCount = 0

            val job =
                launch {
                    awaitHoldAndFlush(
                        onTick = {},
                        onRelease = { ticks ->
                            releasedTicks = ticks
                            releaseCount++
                        },
                        timing = RepeatTiming(initialDelayMillis = 400, repeatIntervalMillis = 250),
                        // Never returns on its own — mirrors a gesture that is cancelled instead
                        // of ever seeing waitForUpOrCancellation() return normally.
                        awaitRelease = { CompletableDeferred<Unit>().await() },
                    )
                }

            // t=0 tick, then a repeat at 400 -> 2 ticks accumulated before cancellation at 650
            // (the next repeat is scheduled for exactly 650, but cancellation wins the race).
            advanceTimeBy(650)
            job.cancel()
            job.join()

            assertEquals(2, releasedTicks)
            assertEquals(1, releaseCount)
        }

    @Test
    fun `cancellation after the hold has started still flushes exactly once`() =
        runTest {
            var releaseCount = 0
            var releasedTicks = -1

            val job =
                launch {
                    awaitHoldAndFlush(
                        onTick = {},
                        onRelease = { ticks ->
                            releasedTicks = ticks
                            releaseCount++
                        },
                        timing = RepeatTiming(initialDelayMillis = 400, repeatIntervalMillis = 250),
                        awaitRelease = { CompletableDeferred<Unit>().await() },
                    )
                }
            // Let the coroutine actually start (fire its t=0 tick) before cancelling — cancelling
            // a job that never got to run its body at all is not a scenario this gesture can hit,
            // since awaitFirstDown() has already suspended (and resumed) before the hold starts.
            advanceTimeBy(1)
            job.cancel()
            job.join()

            assertEquals(1, releaseCount)
            assertTrue(releasedTicks >= 1)
        }
}
