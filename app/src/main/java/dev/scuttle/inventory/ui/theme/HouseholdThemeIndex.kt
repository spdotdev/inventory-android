package dev.scuttle.inventory.ui.theme

import kotlin.math.absoluteValue

// Pure index math for per-household theming, deliberately kept free of any
// Compose types so it can be unit-tested on a plain JVM without loading the
// palette/icon tables. Counts MUST match the list sizes in HouseholdTheme.kt.

internal const val HOUSEHOLD_ACCENT_COUNT = 8
internal const val HOUSEHOLD_ICON_COUNT = 8

internal fun householdAccentIndex(id: Long): Int =
    (id % HOUSEHOLD_ACCENT_COUNT).toInt().absoluteValue

// A different stride than the accent so the two don't move in lockstep for
// small, sequential ids.
internal fun householdIconIndex(id: Long): Int =
    ((id * 3 + 1) % HOUSEHOLD_ICON_COUNT).toInt().absoluteValue
