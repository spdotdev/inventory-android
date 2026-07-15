package dev.scuttle.inventory.ui.common

/** A household as [HouseholdPickerSheet] needs it: enough to label a row and pick it. */
data class HouseholdOption(
    val id: Long,
    val name: String,
)
