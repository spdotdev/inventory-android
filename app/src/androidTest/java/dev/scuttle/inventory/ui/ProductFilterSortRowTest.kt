package dev.scuttle.inventory.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.ui.common.SortOrder
import dev.scuttle.inventory.ui.products.ProductFilterSortRow
import org.junit.Rule
import org.junit.Test

/**
 * The filter/sort row had the same defect as the product card (#31): the two chips are sized to
 * their content and take their width first, so the sort label — the only flexible thing in the
 * row — was left with the remainder and ellipsized down to "…". It only shows at font scales
 * above ~1.3, which is why the flow tests, which run at the device's scale, never caught it.
 */
class ProductFilterSortRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun renderAtFontScale(fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                MaterialTheme {
                    // A narrow-but-ordinary phone width, so the assertion isn't a property of
                    // whichever device happens to run the suite.
                    Box(Modifier.requiredWidth(360.dp)) {
                        ProductFilterSortRow(
                            mandatoryOnly = false,
                            outOfStockOnly = false,
                            sort = SortOrder.NAME_ASC,
                            onToggleMandatory = {},
                            onToggleOutOfStock = {},
                            onSortSelect = {},
                        )
                    }
                }
            }
        }
    }

    private fun sortLabelLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithTag("sort-menu")
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.first()
    }

    /**
     * Oracle: every character of the label is actually painted. `hasVisualOverflow` is not
     * usable here — it reports true even when the text got the width it asked for (301.5px
     * wanted, 302px granted), so it would fail a perfectly readable label.
     */
    private fun truncatedChars(): Int {
        val layout = sortLabelLayout()
        val visible = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
        return layout.layoutInput.text.length - visible
    }

    @Test
    fun sort_label_is_readable_at_default_font_scale() {
        renderAtFontScale(1.0f)

        assert(truncatedChars() == 0) {
            "Sort label lost ${truncatedChars()} characters to ellipsis at font scale 1.0"
        }
    }

    @Test
    fun sort_label_is_readable_at_large_font_scale() {
        renderAtFontScale(1.6f)

        assert(truncatedChars() == 0) {
            "Sort label lost ${truncatedChars()} characters to ellipsis at font scale 1.6 — " +
                "the filter chips are starving it of width"
        }
    }
}
