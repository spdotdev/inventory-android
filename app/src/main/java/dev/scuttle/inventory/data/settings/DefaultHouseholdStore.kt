package dev.scuttle.inventory.data.settings

interface DefaultHouseholdStore {
    fun get(): Long?
    fun set(householdId: Long)
    fun clear()
}
