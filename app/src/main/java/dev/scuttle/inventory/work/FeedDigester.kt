package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.dto.FeedEventDto
import dev.scuttle.inventory.data.settings.NotificationPrefs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DEFAULT_ACTIVITY_COUNT = 1
private const val UNGROUPED_HOUSEHOLD_ID = 0L

private const val TYPE_MEMBER_JOINED = "member_joined"
private const val TYPE_ROLE_CHANGED = "role_changed"
private const val TYPE_ACTIVITY = "activity"

/** Sealed set of notifications the app may post, derived from a feed poll page. */
sealed interface PlannedNotification {
    data class MemberJoined(
        val eventId: Long,
        val memberName: String?,
        val householdName: String?,
    ) : PlannedNotification

    data class RoleChanged(
        val eventId: Long,
        val newRole: String?,
        val householdName: String?,
    ) : PlannedNotification

    data class ActivityDigest(
        val householdId: Long,
        val householdName: String?,
        val changeCount: Int,
    ) : PlannedNotification
}

/**
 * Pure grouping of a feed poll page into notifications to post. No Android
 * imports so it's directly unit-testable on the JVM.
 */
object FeedDigester {
    fun digest(
        events: List<FeedEventDto>,
        prefs: NotificationPrefs,
    ): List<PlannedNotification> {
        val result = mutableListOf<PlannedNotification>()
        val digestTotals = linkedMapOf<Long, ActivityDigestAccumulator>()

        for (event in events) {
            when (event.type) {
                TYPE_MEMBER_JOINED -> {
                    if (prefs.householdEventsEnabled) {
                        result.add(
                            PlannedNotification.MemberJoined(
                                eventId = event.id,
                                memberName = memberName(event.payload),
                                householdName = event.household?.name,
                            ),
                        )
                    }
                }
                TYPE_ROLE_CHANGED -> {
                    if (prefs.householdEventsEnabled) {
                        result.add(
                            PlannedNotification.RoleChanged(
                                eventId = event.id,
                                newRole = newRole(event.payload),
                                householdName = event.household?.name,
                            ),
                        )
                    }
                }
                TYPE_ACTIVITY -> {
                    if (prefs.activityDigestEnabled) {
                        val householdId = event.household?.id ?: UNGROUPED_HOUSEHOLD_ID
                        val accumulator =
                            digestTotals.getOrPut(householdId) {
                                ActivityDigestAccumulator(event.household?.name)
                            }
                        accumulator.count += activityCount(event.payload)
                    }
                }
                else -> Unit
            }
        }

        digestTotals.forEach { (householdId, accumulator) ->
            result.add(
                PlannedNotification.ActivityDigest(
                    householdId = householdId,
                    householdName = accumulator.householdName,
                    changeCount = accumulator.count,
                ),
            )
        }

        return result
    }

    private fun memberName(payload: JsonObject?): String? = payload?.get("name")?.jsonPrimitive?.contentOrNull

    private fun newRole(payload: JsonObject?): String? =
        payload
            ?.get("role")
            ?.jsonObject
            ?.get("to")
            ?.jsonPrimitive
            ?.contentOrNull

    private fun activityCount(payload: JsonObject?): Int =
        payload?.get("count")?.jsonPrimitive?.intOrNull ?: DEFAULT_ACTIVITY_COUNT

    private class ActivityDigestAccumulator(
        val householdName: String?,
    ) {
        var count: Int = 0
    }
}
