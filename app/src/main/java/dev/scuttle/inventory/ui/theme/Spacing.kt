package dev.scuttle.inventory.ui.theme

import androidx.compose.ui.unit.dp

/**
 * GAP4-L3 (scoped): a small shared spacing scale for NEW Compose code going forward.
 *
 * The codebase has ~215 hand-typed `.dp` literals with no shared tokens, which lets
 * padding drift between screens (the audit named Auth/Dashboard/Scanner specifically).
 * The values below were derived from the actual literal frequency across the app
 * (`grep -rohE '[0-9]+\.dp' app/src/main/java | sort | uniq -c | sort -rn`), not picked
 * arbitrarily — 16.dp, 8.dp, 12.dp, 24.dp and 4.dp are already the five most common
 * values in the codebase, so this scale documents the convention that already exists
 * rather than inventing a new one:
 *
 * ```
 * 44  16.dp
 * 40   8.dp
 * 25  12.dp
 * 24  24.dp
 * 18   4.dp
 * ```
 *
 * **Scope**: this migration deliberately covers only the three screens the audit
 * flagged as drifting (AuthScreen, DashboardScreen, ScannerScreen) — see their diffs in
 * the same commit. The remaining ~190 literals elsewhere are intentionally left alone;
 * adopt [Spacing] organically in new/touched code rather than doing a blanket
 * find-and-replace across the whole app in one pass.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
