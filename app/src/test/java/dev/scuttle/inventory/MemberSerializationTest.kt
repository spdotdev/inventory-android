package dev.scuttle.inventory

import dev.scuttle.inventory.data.dto.HouseholdDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun household_dto_decodes_role_and_capabilities() {
        val decoded =
            json.decodeFromString(
                HouseholdDto.serializer(),
                """{"id":1,"name":"Home","join_code":"AAAA-1111","role":"admin",""" +
                    """"can_restructure":true,"can_manage_members":true}""",
            )

        assertTrue(decoded.can_restructure)
        assertTrue(decoded.can_manage_members)
    }

    @Test
    fun a_member_role_decodes_false_capabilities() {
        val decoded =
            json.decodeFromString(
                HouseholdDto.serializer(),
                """{"id":1,"name":"Home","join_code":"AAAA-1111","role":"member",""" +
                    """"can_restructure":false,"can_manage_members":false}""",
            )

        assertFalse(decoded.can_restructure)
        assertFalse(decoded.can_manage_members)
    }
}
