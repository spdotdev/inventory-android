package dev.scuttle.inventory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps Dispatchers.Main for a test dispatcher so viewModelScope runs under test.
 * Always builds its own [TestCoroutineScheduler] rather than defaulting to
 * `UnconfinedTestDispatcher()`'s "reuse whatever scheduler Dispatchers.Main is
 * already using" fallback — without this, a test that leaves an uncaught
 * coroutine exception recorded on the (shared) scheduler could fail an
 * unrelated later test instead of itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
