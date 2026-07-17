package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.MemberDto
import dev.scuttle.inventory.data.member.MemberRepository
import dev.scuttle.inventory.ui.members.MembersViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
