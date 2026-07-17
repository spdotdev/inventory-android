package dev.scuttle.inventory.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MemberDto(
    val id: Long,
    val name: String,
    val role: String,
    val joined_at: String?,
)

@Serializable
data class MemberListResponse(
    val data: List<MemberDto>,
)

@Serializable
data class MemberResponse(
    val data: MemberDto,
)

@Serializable
data class UpdateMemberRoleRequest(
    val role: String,
)

@Serializable
data class TransferOwnershipRequest(
    val user_id: Long,
)
