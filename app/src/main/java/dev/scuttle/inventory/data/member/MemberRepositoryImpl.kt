package dev.scuttle.inventory.data.member

import dev.scuttle.inventory.data.api.MemberApi
import dev.scuttle.inventory.data.dto.MemberDto
import dev.scuttle.inventory.data.dto.TransferOwnershipRequest
import dev.scuttle.inventory.data.dto.UpdateMemberRoleRequest
import javax.inject.Inject

class MemberRepositoryImpl
    @Inject
    constructor(
        private val api: MemberApi,
    ) : MemberRepository {
        override suspend fun list(householdId: Long): List<MemberDto> = api.list(householdId).data

        override suspend fun updateRole(
            householdId: Long,
            userId: Long,
            role: String,
        ): MemberDto = api.updateRole(householdId, userId, UpdateMemberRoleRequest(role)).data

        override suspend fun remove(
            householdId: Long,
            userId: Long,
        ) = api.remove(householdId, userId)

        override suspend fun transferOwnership(
            householdId: Long,
            userId: Long,
        ) = api.transferOwnership(householdId, TransferOwnershipRequest(userId))
    }
