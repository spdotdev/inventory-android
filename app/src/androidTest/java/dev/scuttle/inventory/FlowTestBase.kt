@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.scuttle.inventory

import android.content.Context
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    private val clearTokenRule = object : ExternalResource() {
        override fun before() {
            tokenStore.clear()
        }
    }

    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(mockServer)
        .around(hiltRule)
        .around(object : ExternalResource() {
            override fun before() { hiltRule.inject() }
        })
        .around(clearTokenRule)
        .around(composeRule)

    protected val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    protected fun fixture(name: String): String =
        testContext.assets.open("fixtures/$name").bufferedReader().readText().trim()
}
