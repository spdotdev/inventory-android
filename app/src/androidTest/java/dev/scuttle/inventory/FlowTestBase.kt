@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory

import android.content.Context
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dev.scuttle.inventory.data.storage.TokenStore
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import javax.inject.Inject

/**
 * Base for all flow tests. Rule execution order:
 * 1. MockWebServer starts (so TestNetworkModule reads the right URL)
 * 2. Hilt builds the graph + injects fields
 * 3. Token is cleared (before the Activity launches)
 * 4. Compose rule launches MainActivity
 */
abstract class FlowTestBase {
    val mockServer = MockWebServerRule()
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var tokenStore: TokenStore

    private val clearTokenRule =
        object : ExternalResource() {
            override fun before() {
                tokenStore.clear()
            }
        }

    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(mockServer)
            .around(hiltRule)
            .around(
                object : ExternalResource() {
                    override fun before() {
                        hiltRule.inject()
                    }
                },
            ).around(clearTokenRule)
            .around(composeRule)

    protected val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    protected fun fixture(name: String): String =
        testContext.assets
            .open(
                "fixtures/$name",
            ).bufferedReader()
            .readText()
            .trim()

    /**
     * Tap a row's NAME/leading area, not its node center: performClick's
     * center tap lands on whatever trailing interactive cluster the row
     * carries — a product row's quantity stepper, or (found investigating the
     * CI-only failures behind DeleteShelfFlowTest/DeleteShelfStrategyFlowTest/
     * DeleteLocationFlowTest — a printToLog dump of BOTH composition roots on
     * real CI hardware caught it directly: the "second root" was the Rename
     * dialog) an `EditableRow`'s trailing rename/reorder icon buttons. Either
     * way the click lands on a DIFFERENT element than the one under test —
     * for a disabled "−" (qty 0) it silently swallows the click and the
     * card's own onClick never fires; for EditableRow it fires `onRename`
     * instead of toggling selection, with no error either way. This is
     * screen-size/density dependent (the row's geometric center only
     * overlaps the trailing cluster at SOME width/density combinations),
     * which is exactly why it reproduced deterministically on CI's emulator
     * profile and never once locally across dozens of runs on a
     * higher-resolution AVD. Humans naturally tap the name/leading text,
     * which is why manual testing never catches it either (originally found
     * for products via the 06a989b layout shift; nightly-emulator + phone
     * both failed the same way).
     *
     * The name/leading content sits at the row's top-left-ish region for
     * every row shape this app has (a product card's name is its top line
     * since #31; an EditableRow's checkbox/text lead the row on the left),
     * so one fixed offset works for both — trailing content always owns the
     * right side and/or lower half instead.
     */
    protected fun androidx.compose.ui.test.SemanticsNodeInteraction.clickNameArea() =
        performTouchInput { click(percentOffset(0.15f, 0.2f)) }
}
