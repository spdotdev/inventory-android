package dev.scuttle.inventory.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CompletableDeferred
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
                // waitForUpOrCancellation() is only callable directly on this restricted
                // AwaitPointerEventScope, so it can't be handed to awaitHoldAndFlush as a
                // captured suspend lambda directly (that call is itself a plain — non-restricted
                // — suspend function). Bridge the two with a CompletableDeferred instead: the
                // hold-and-flush logic runs on its own child job (launch is a regular, unrestricted
                // CoroutineScope member) and is released once the pointer goes up. If the gesture
                // itself is cancelled, that child job is cancelled as part of this coroutineScope,
                // and `awaitHoldAndFlush`'s `finally` still fires `onRelease` with the ticks
                // accumulated so far — see its doc comment for why that's cancellation-safe.
                val released = CompletableDeferred<Unit>()
                launch {
                    awaitHoldAndFlush(onTick, onRelease, timing) { released.await() }
                }
                waitForUpOrCancellation()
                released.complete(Unit)
            }
        }
    }

/**
 * H1: accumulates hold ticks and guarantees [onRelease] fires with the final tick count exactly
 * once, whether [awaitRelease] returns normally (pointer up) OR the surrounding coroutine is
 * cancelled (gesture cancellation from navigating away, backgrounding, a config change, etc.).
 * Before this fix, [onRelease] only fired after `waitForUpOrCancellation()` returned normally —
 * a cancelled gesture skipped it and the locally-accumulated delta (and the server mutation it
 * triggers) was silently dropped.
 *
 * [onRelease] itself is a plain (non-suspend) callback — see `ProductsPane`/
 * `ProductDetailScreen`'s wiring, both of which call a non-suspend `ProductsViewModel`/
 * `ProductDetailViewModel` function that launches its own network call on `viewModelScope`
 * internally. That means firing it from `finally` during cancellation needs no
 * `NonCancellable` wrapper: nothing here suspends after cancellation is signalled, so there is
 * no "suspend call in a cancelled scope" hazard — `onRelease` just schedules the flush onto the
 * ViewModel's own scope, which outlives this composable's gesture coroutine.
 *
 * Pulled out of [repeatingClickable] (rather than inlined in the gesture block) so this
 * accumulate-and-flush behavior is unit-testable with plain `kotlinx-coroutines-test`, without
 * any Compose gesture/pointer-input simulation.
 */
internal suspend fun awaitHoldAndFlush(
    onTick: () -> Unit,
    onRelease: (ticks: Int) -> Unit,
    timing: RepeatTiming,
    awaitRelease: suspend () -> Unit,
) = coroutineScope {
    var ticks = 0
    try {
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
        awaitRelease()
        repeatJob.cancel()
    } finally {
        onRelease(ticks)
    }
}
