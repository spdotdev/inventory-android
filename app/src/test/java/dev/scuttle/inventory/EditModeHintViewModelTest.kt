package dev.scuttle.inventory

import dev.scuttle.inventory.data.settings.HINT_EDIT_MODE_PENCIL
import dev.scuttle.inventory.data.settings.HintsStore
import dev.scuttle.inventory.ui.common.EditModeHintViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditModeHintViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeHintsStore(
        private var seen: Boolean = false,
        private val failMarkSeen: Boolean = false,
    ) : HintsStore {
        var markSeenCalled = false

        override fun hasSeen(hintId: String): Boolean = seen

        override fun markSeen(hintId: String) {
            markSeenCalled = true
            if (failMarkSeen) error("store write failed")
            seen = true
        }
    }

    @Test
    fun initial_visible_is_true_when_hint_not_yet_seen() {
        val viewModel = EditModeHintViewModel(FakeHintsStore(seen = false))

        assertTrue(viewModel.visible.value)
    }

    @Test
    fun initial_visible_is_false_when_hint_already_seen() {
        val viewModel = EditModeHintViewModel(FakeHintsStore(seen = true))

        assertFalse(viewModel.visible.value)
    }

    @Test
    fun dismiss_marks_seen_and_hides_the_hint() =
        runTest {
            val store = FakeHintsStore(seen = false)
            val viewModel = EditModeHintViewModel(store)

            viewModel.dismiss()

            assertFalse(viewModel.visible.value)
            assertTrue(store.markSeenCalled)
        }

    /**
     * GAP4-L9 gap found in the 2026-07-19 audit: [EditModeHintViewModel.dismiss] has no
     * try/catch around `markSeen`, so a store failure throws inside the `viewModelScope.launch`
     * coroutine before `_visible.value = false` ever runs. It does NOT propagate synchronously
     * out of `dismiss()` (structured concurrency: `viewModelScope`'s `SupervisorJob` has no
     * `CoroutineExceptionHandler`, so the failure is only ever delivered as an *uncaught*
     * coroutine exception, asynchronously, on whatever reports uncaught exceptions for
     * `Dispatchers.Main` — `runTest` would fail the test for it, which is why this test avoids
     * `runTest` and drives the `UnconfinedTestDispatcher` set up by [MainDispatcherRule]
     * directly). From the caller's point of view `dismiss()` just returns normally having done
     * nothing: `visible` stays stuck true forever, with no error surfaced anywhere a caller can
     * observe — the hint can never be dismissed on a device whose `HintsStore` write fails.
     * Documenting the current behavior rather than fixing it, per audit scope.
     */
    @Test
    fun dismiss_leaves_hint_visible_when_the_store_write_fails() {
        val store = FakeHintsStore(seen = false, failMarkSeen = true)
        val viewModel = EditModeHintViewModel(store)

        viewModel.dismiss()

        assertTrue(store.markSeenCalled)
        assertTrue(viewModel.visible.value)
    }

    @Test
    fun hint_id_used_is_the_shared_edit_mode_pencil_constant() {
        var idPassedToHasSeen: String? = null
        val store =
            object : HintsStore {
                override fun hasSeen(hintId: String): Boolean {
                    idPassedToHasSeen = hintId
                    return false
                }

                override fun markSeen(hintId: String) = Unit
            }

        EditModeHintViewModel(store)

        assertEquals(HINT_EDIT_MODE_PENCIL, idPassedToHasSeen)
    }
}
