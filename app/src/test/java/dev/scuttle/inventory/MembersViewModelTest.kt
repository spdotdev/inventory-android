package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.MemberDto
import dev.scuttle.inventory.data.member.MemberRepository
import dev.scuttle.inventory.ui.members.MembersViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FakeMemberRepository : MemberRepository {
    var members = mutableListOf<MemberDto>()
    var lastRoleUpdate: Pair<Long, String>? = null
    var lastRemovedId: Long? = null
    var lastTransferTargetId: Long? = null

    override suspend fun list(householdId: Long): List<MemberDto> = members

    override suspend fun updateRole(
        householdId: Long,
        userId: Long,
        role: String,
    ): MemberDto {
        lastRoleUpdate = userId to role
        val index = members.indexOfFirst { it.id == userId }
        val updated = members[index].copy(role = role)
        members[index] = updated
        return updated
    }

    override suspend fun remove(
        householdId: Long,
        userId: Long,
    ) {
        lastRemovedId = userId
        members.removeIf { it.id == userId }
    }

    override suspend fun transferOwnership(
        householdId: Long,
        userId: Long,
    ) {
        lastTransferTargetId = userId
        val newOwnerIndex = members.indexOfFirst { it.id == userId }
        val oldOwnerIndex = members.indexOfFirst { it.role == "owner" }
        members[newOwnerIndex] = members[newOwnerIndex].copy(role = "owner")
        if (oldOwnerIndex != -1) members[oldOwnerIndex] = members[oldOwnerIndex].copy(role = "admin")
    }
}

class MembersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loading_populates_the_member_list() =
        runTest {
            val repo = FakeMemberRepository().apply { members.add(MemberDto(1, "Stan", "owner", null)) }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)

            assertEquals(
                listOf("Stan"),
                viewModel.state.value.members
                    .map { it.name },
            )
        }

    @Test
    fun promoting_a_member_sends_admin() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "member", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)

            viewModel.promote(2L)

            assertEquals(2L to "admin", repo.lastRoleUpdate)
        }

    @Test
    fun removing_a_member_drops_them_from_state() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "member", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)

            viewModel.remove(2L)

            assertEquals(
                listOf("Owner"),
                viewModel.state.value.members
                    .map { it.name },
            )
        }

    @Test
    fun transferring_ownership_swaps_roles_in_state() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "admin", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)

            viewModel.transferOwnership(2L)

            assertEquals(
                "owner",
                viewModel.state.value.members
                    .first { it.id == 2L }
                    .role,
            )
            assertEquals(
                "admin",
                viewModel.state.value.members
                    .first { it.id == 1L }
                    .role,
            )
        }

    @Test
    fun promoting_a_member_emits_a_role_change_event_with_the_inverse_role() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "member", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)

            viewModel.promote(2L)

            val event =
                viewModel.state.value.roleChangeEvents
                    .firstOrNull()
            assertEquals(2L, event?.userId)
            assertEquals("admin", event?.newRole)
            assertEquals("member", event?.previousRole)
        }

    @Test
    fun undoing_a_role_change_reverts_to_the_previous_role_without_emitting_a_new_event() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "member", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)
            viewModel.promote(2L)
            val event =
                viewModel.state.value.roleChangeEvents
                    .first()

            viewModel.undoRoleChange(event)

            assertEquals(
                "member",
                viewModel.state.value.members
                    .first { it.id == 2L }
                    .role,
            )
            // Undo is a correction, not a fresh action — it must not itself
            // enqueue a new event or dequeue the existing one.
            assertEquals(listOf(event), viewModel.state.value.roleChangeEvents)
        }

    @Test
    fun consuming_the_role_change_event_clears_it() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "member", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)
            viewModel.promote(2L)

            viewModel.consumeRoleChangeEvent()

            assertTrue(
                viewModel.state.value.roleChangeEvents
                    .isEmpty(),
            )
        }

    @Test
    fun a_second_role_change_while_the_first_is_still_queued_is_not_dropped() =
        runTest {
            // H2 regression: a lone `roleChangeEvent` slot would replace the
            // first change with the second, silently dropping the first's Undo.
            // The queue must retain BOTH until each is individually dequeued.
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Alice", "member", null))
                    members.add(MemberDto(3, "Bob", "member", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)

            viewModel.promote(2L)
            viewModel.promote(3L)

            val events = viewModel.state.value.roleChangeEvents
            assertEquals(2, events.size)
            assertEquals(2L, events[0].userId)
            assertEquals(3L, events[1].userId)

            // Consuming only dequeues the HEAD — the second event survives.
            viewModel.consumeRoleChangeEvent()
            assertEquals(
                listOf(3L),
                viewModel.state.value.roleChangeEvents
                    .map { it.userId },
            )

            viewModel.consumeRoleChangeEvent()
            assertTrue(
                viewModel.state.value.roleChangeEvents
                    .isEmpty(),
            )
        }

    /**
     * Regression test for the "stale viewerRole after transfer-ownership" bug: the
     * ORIGINAL viewer's own row must never again read as "self" once they've been
     * demoted by their own transferOwnership() call, even though the member LIST
     * (correctly) now shows a DIFFERENT member holding "owner". `MembersScreen`
     * derives isSelf from a `viewerRole` prop this ViewModel doesn't own — what this
     * ViewModel CAN guarantee is that `ownershipTransferCount` ticks in lockstep
     * with the (already-correct) member-list refresh, giving the caller a signal to
     * refresh that stale prop rather than leaving a demoted viewer's UI offering an
     * action (Transfer ownership) on someone else's row.
     */
    @Test
    fun transferring_ownership_signals_the_caller_to_refresh_viewer_role() =
        runTest {
            val repo =
                FakeMemberRepository().apply {
                    members.add(MemberDto(1, "Owner", "owner", null))
                    members.add(MemberDto(2, "Plain", "admin", null))
                }
            val viewModel = MembersViewModel(repo)
            viewModel.load(householdId = 1)
            assertEquals(0, viewModel.state.value.ownershipTransferCount)

            viewModel.transferOwnership(2L)

            // The signal fired exactly once, alongside the list refresh that already
            // shows member 2 (not member 1, the original viewer) as "owner" — so a
            // caller comparing against a STALE viewerRole == "owner" would otherwise
            // keep matching member 2's row as "self" for member 1's session.
            assertEquals(1, viewModel.state.value.ownershipTransferCount)
            assertEquals(
                "owner",
                viewModel.state.value.members
                    .first { it.id == 2L }
                    .role,
            )
            assertEquals(
                "admin",
                viewModel.state.value.members
                    .first { it.id == 1L }
                    .role,
            )
        }
}
