package dev.scuttle.inventory.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Delay before a held press starts repeating — long enough that a normal tap never repeats. */
const val REPEAT_INITIAL_DELAY_MILLIS = 400L

/**
 * Interval between repeats once a hold is past [REPEAT_INITIAL_DELAY_MILLIS].
 *
 * GAP-5 M10: each tap on the quantity stepper fires a full ViewModel mutation that is a
 * server round-trip (`ProductsViewModel.increment`/`decrement` -> `ProductRepository.add`/
 * `remove`, and the ProductDetail equivalents) — there is no local source of truth to
 * optimistically apply against per this app's always-online rule. Repeating at the
 * originally-considered ~10/sec would hammer the server with one request per tick. This
 * throttles the *tick* rate (what drives the on-screen count while held) to 4/sec, but the
 * network call itself is NOT one-per-tick either: callers accumulate the held delta locally
 * and fire exactly ONE mutation with the summed amount on release (see
 * `ProductsPane`/`ProductDetailScreen`'s use of [repeatingClickable] — the tick only updates a
 * local counter via `onTick`, `onRelease` is what calls the repository). A raw
 * accumulate-fully-client-side-and-never-call-the-server approach was considered and rejected:
 * it would leave the displayed count as a locally-guessed value for the whole hold, which is
 * exactly the "local state as source of truth" this app's CLAUDE.md forbids — sending once on
 * release keeps the server authoritative while still avoiding a call per tick.
 */
const val REPEAT_INTERVAL_MILLIS = 250L

/**
 * Number of times a tick fires for a press held [heldMillis], given [initialDelayMillis] before
 * the first repeat and [repeatIntervalMillis] between repeats after that. A plain tap (released
 * before [initialDelayMillis] elapses) still counts once — mirrors a normal onClick.
 *
 * Pure and Compose-free so the repeat schedule is covered by a JVM unit test without any gesture
 * simulation.
 */
fun repeatTickCount(
    heldMillis: Long,
    initialDelayMillis: Long = REPEAT_INITIAL_DELAY_MILLIS,
    repeatIntervalMillis: Long = REPEAT_INTERVAL_MILLIS,
): Int =
    when {
        heldMillis < 0 -> 0
        // The immediate tap (t=0) plus every repeat whose scheduled time
        // (initialDelayMillis, +repeatIntervalMillis, +repeatIntervalMillis, ...) has
        // elapsed by heldMillis — so exactly heldMillis == initialDelayMillis already
        // counts the first repeat (2 ticks total), matching repeatingClickable's
        // actual tick schedule.
        heldMillis < initialDelayMillis -> 1
        else -> 2 + ((heldMillis - initialDelayMillis) / repeatIntervalMillis).toInt()
    }

/** [initialDelayMillis]/[repeatIntervalMillis] bundled so [repeatingClickable] stays under the
 * repo's max-parameter-count gate. */
data class RepeatTiming(
    val initialDelayMillis: Long = REPEAT_INITIAL_DELAY_MILLIS,
    val repeatIntervalMillis: Long = REPEAT_INTERVAL_MILLIS,
)

/**
 * Press-and-hold auto-repeat for a stepper button, shared by `ProductsPane`'s row-level
 * stepper and `ProductDetailScreen`'s stepper (GAP-5 M10) so both behave identically: [onTick]
 * fires once immediately on press, then repeats every [RepeatTiming.repeatIntervalMillis] once
 * [RepeatTiming.initialDelayMillis] has elapsed, until the pointer is released or cancelled — at
 * which point [onRelease] fires exactly once, telling the caller how many ticks fired so it can
 * send one accumulated mutation instead of one per tick.
 */
fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onTick: () -> Unit,
    onRelease: (ticks: Int) -> Unit,
    timing: RepeatTiming = RepeatTiming(),
): Modifier =
    // coroutineScope wraps the WHOLE gesture (not just the launch{}) — AwaitPointerEventScope
    // (awaitEachGesture's receiver) is a restricted suspend scope that can only call its own
    // await*/waitFor* members directly; a bare `coroutineScope { }` *inside* that block doesn't
    // compile ("Restricted suspending functions can invoke ... only on their restricted
    // coroutine scope"). Opening the CoroutineScope one level up, before awaitEachGesture, keeps
    // every direct call inside the gesture block a legitimate AwaitPointerEventScope member
    // (awaitFirstDown/waitForUpOrCancellation), while `launch` still resolves to this outer
    // CoroutineScope for the ticking job.
    this.pointerInput(interactionSource, enabled) {
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                if (!enabled) return@awaitEachGesture
                var ticks = 0
                val repeatJob =
                    launch {
                        onTick()
                        ticks = 1
                        delay(timing.initialDelayMillis)
                        while (true) {
                            onTick()
                            ticks++
                            delay(timing.repeatIntervalMillis)
                        }
                    }
                waitForUpOrCancellation()
                repeatJob.cancel()
                onRelease(ticks)
            }
        }
    }
