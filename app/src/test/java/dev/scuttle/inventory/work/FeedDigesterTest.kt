package dev.scuttle.inventory.work

import dev.scuttle.inventory.data.dto.FeedEventDto
import dev.scuttle.inventory.data.dto.FeedHouseholdDto
import dev.scuttle.inventory.data.settings.NotificationPrefs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUSEHOLD_ONE = 1L
private const val HOUSEHOLD_TWO = 2L

class FeedDigesterTest {
    private fun memberJoinedEvent(
        id: Long,
        householdId: Long? = HOUSEHOLD_ONE,
        householdName: String? = "Home",
        name: String? = "Ann",
    ): FeedEventDto =
        FeedEventDto(
            id = id,
            type = "member_joined",
            household = householdId?.let { FeedHouseholdDto(it, householdName) },
            payload = buildJsonObject { put("name", name) },
        )

    private fun roleChangedEvent(
        id: Long,
        householdId: Long = HOUSEHOLD_ONE,
        householdName: String? = "Home",
        newRole: String? = "admin",
    ): FeedEventDto =
        FeedEventDto(
            id = id,
            type = "role_changed",
            household = FeedHouseholdDto(householdId, householdName),
            payload =
                buildJsonObject {
                    put(
                        "role",
                        buildJsonObject { put("to", newRole) },
                    )
                },
        )

    private fun activityEvent(
        id: Long,
        householdId: Long = HOUSEHOLD_ONE,
        householdName: String? = "Home",
        count: Int? = 1,
    ): FeedEventDto =
        FeedEventDto(
            id = id,
            type = "activity",
            household = FeedHouseholdDto(householdId, householdName),
            payload = count?.let { buildJsonObject { put("count", it) } } ?: JsonObject(emptyMap()),
        )

    @Test
    fun mixed_page_produces_per_event_notifications_and_a_digest() {
        val prefs = NotificationPrefs(householdEventsEnabled = true, activityDigestEnabled = true)
        val events =
            listOf(
                memberJoinedEvent(id = 1),
                roleChangedEvent(id = 2),
                activityEvent(id = 3),
            )

        val result = FeedDigester.digest(events, prefs)

        assertEquals(3, result.size)
        val joined = result.filterIsInstance<PlannedNotification.MemberJoined>().single()
        assertEquals(1L, joined.eventId)
        assertEquals("Ann", joined.memberName)
        assertEquals("Home", joined.householdName)

        val roleChanged = result.filterIsInstance<PlannedNotification.RoleChanged>().single()
        assertEquals(2L, roleChanged.eventId)
        assertEquals("admin", roleChanged.newRole)
        assertEquals("Home", roleChanged.householdName)

        val digest = result.filterIsInstance<PlannedNotification.ActivityDigest>().single()
        assertEquals(HOUSEHOLD_ONE, digest.householdId)
        assertEquals("Home", digest.householdName)
        assertEquals(1, digest.changeCount)
    }

    @Test
    fun digest_sums_counts_per_household() {
        val prefs = NotificationPrefs(activityDigestEnabled = true, householdEventsEnabled = false)
        val events =
            listOf(
                activityEvent(id = 1, householdId = HOUSEHOLD_ONE, count = 2),
                activityEvent(id = 2, householdId = HOUSEHOLD_ONE, count = 3),
                activityEvent(id = 3, householdId = HOUSEHOLD_TWO, count = null),
            )

        val result = FeedDigester.digest(events, prefs)

        val digests = result.filterIsInstance<PlannedNotification.ActivityDigest>()
        assertEquals(2, digests.size)
        val householdOneDigest = digests.single { it.householdId == HOUSEHOLD_ONE }
        assertEquals(5, householdOneDigest.changeCount)
        val householdTwoDigest = digests.single { it.householdId == HOUSEHOLD_TWO }
        assertEquals(1, householdTwoDigest.changeCount)
    }

    @Test
    fun household_events_disabled_drops_joined_and_role_events() {
        val prefs = NotificationPrefs(householdEventsEnabled = false, activityDigestEnabled = true)
        val events =
            listOf(
                memberJoinedEvent(id = 1),
                roleChangedEvent(id = 2),
                activityEvent(id = 3),
            )

        val result = FeedDigester.digest(events, prefs)

        assertTrue(result.none { it is PlannedNotification.MemberJoined })
        assertTrue(result.none { it is PlannedNotification.RoleChanged })
        assertEquals(1, result.filterIsInstance<PlannedNotification.ActivityDigest>().size)
    }

    @Test
    fun activity_digest_disabled_drops_activity_events() {
        val prefs = NotificationPrefs(householdEventsEnabled = true, activityDigestEnabled = false)
        val events =
            listOf(
                memberJoinedEvent(id = 1),
                activityEvent(id = 2),
            )

        val result = FeedDigester.digest(events, prefs)

        assertTrue(result.none { it is PlannedNotification.ActivityDigest })
        assertEquals(1, result.size)
    }

    @Test
    fun unknown_event_type_is_dropped_without_throwing() {
        val prefs = NotificationPrefs(householdEventsEnabled = true, activityDigestEnabled = true)
        val events =
            listOf(
                FeedEventDto(
                    id = 1,
                    type = "something_new",
                    household = FeedHouseholdDto(HOUSEHOLD_ONE, "Home"),
                    payload = null,
                ),
                memberJoinedEvent(id = 2),
            )

        val result = FeedDigester.digest(events, prefs)

        assertEquals(1, result.size)
        assertTrue(result.single() is PlannedNotification.MemberJoined)
    }

    @Test
    fun null_household_groups_under_household_id_zero() {
        val prefs = NotificationPrefs(activityDigestEnabled = true, householdEventsEnabled = false)
        val events =
            listOf(
                activityEvent(id = 1, householdId = HOUSEHOLD_ONE).let {
                    it.copy(household = null)
                },
            )

        val result = FeedDigester.digest(events, prefs)

        val digest = result.filterIsInstance<PlannedNotification.ActivityDigest>().single()
        assertEquals(0L, digest.householdId)
        assertEquals(null, digest.householdName)
    }
}
