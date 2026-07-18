package dev.scuttle.inventory.ui.households

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.scuttle.inventory.data.HierarchyStore
import dev.scuttle.inventory.data.dto.HouseholdDto
import dev.scuttle.inventory.data.dto.LocationDto
import dev.scuttle.inventory.data.dto.ProductDto
import dev.scuttle.inventory.data.dto.ShelfDto
import dev.scuttle.inventory.data.household.HouseholdRepository
import dev.scuttle.inventory.data.location.LocationRepository
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.data.product.ProductRepository
import dev.scuttle.inventory.data.shelf.ShelfRepository
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/**
 * `HouseholdEditScreen` gates rename (name field + Save) and the theme swatches
 * behind `household.can_restructure` — a Member sees only a plain `Text(name)`
 * instead, matching the client-side gate applied to locations/shelves edit mode
 * (see the screen's own doc comment) and the server's `HouseholdPolicy@restructure`
 * check it fronts. Nothing exercised this before: every other screen this branch
 * touched (StorageOverviewViewModel/ShelvesViewModel/HouseholdsViewModel) got a
 * ViewModel-state test, but this gate lives purely in the Composable body.
 *
 * Rendered in isolation, same pattern as `EditableRowTest`/`ProductFilterSortRowTest`:
 * `createComposeRule()`, no Activity/Hilt — `HouseholdsViewModel` is a plain
 * constructor-injected class, so a real instance over fake repositories (same
 * fakes/pattern as `HouseholdsViewModelTest`, restated here since JVM `src/test`
 * helpers aren't visible from this `androidTest` source set) is enough to drive it.
 */
class HouseholdEditScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class FakeHouseholdRepository(
        private val household: HouseholdDto,
    ) : HouseholdRepository {
        override fun getCached(): List<HouseholdDto>? = null

        override suspend fun list(): List<HouseholdDto> = listOf(household)

        override suspend fun create(name: String): HouseholdDto = household

        override suspend fun join(code: String): HouseholdDto = household

        override suspend fun leave(householdId: Long) = Unit

        override suspend fun delete(
            householdId: Long,
            nameConfirmation: String,
        ) = Unit
    }

    private object EmptyLocations : LocationRepository {
        override fun getCached(householdId: Long): List<LocationDto>? = null

        override suspend fun list(householdId: Long) = emptyList<LocationDto>()

        override suspend fun create(
            householdId: Long,
            name: String,
            type: String,
        ) = throw NotImplementedError()
    }

    private object EmptyShelves : ShelfRepository {
        override fun getCached(
            householdId: Long,
            locationId: Long,
        ): List<ShelfDto>? = null

        override suspend fun list(
            householdId: Long,
            locationId: Long,
        ) = emptyList<ShelfDto>()

        override suspend fun create(
            householdId: Long,
            locationId: Long,
            name: String,
        ) = throw NotImplementedError()
    }

    private object EmptyProducts : ProductRepository {
        override fun getCached(
            householdId: Long,
            shelfId: Long,
        ): List<ProductDto>? = null

        override suspend fun list(
            householdId: Long,
            shelfId: Long,
        ) = emptyList<ProductDto>()

        override suspend fun create(
            householdId: Long,
            shelfId: Long,
            name: String,
            quantity: Int,
            code: String?,
        ) = throw NotImplementedError()

        override suspend fun update(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            edit: ProductEdit,
        ) = throw NotImplementedError()

        override suspend fun add(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = throw NotImplementedError()

        override suspend fun remove(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            amount: Int,
        ) = throw NotImplementedError()

        override suspend fun move(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            targetShelfId: Long,
        ) = throw NotImplementedError()

        override suspend fun uploadImage(
            householdId: Long,
            shelfId: Long,
            productId: Long,
            imageUri: Uri,
            mimeType: String,
        ) = throw NotImplementedError()

        override suspend fun delete(
            householdId: Long,
            shelfId: Long,
            productId: Long,
        ) = "batch"
    }

    private fun household(
        canRestructure: Boolean,
        role: String = if (canRestructure) "admin" else "member",
    ) = HouseholdDto(
        id = 1,
        name = "Garage",
        join_code = "AAAA-1111",
        role = role,
        can_restructure = canRestructure,
        can_manage_members = canRestructure,
    )

    private fun render(
        canRestructure: Boolean,
        role: String = if (canRestructure) "admin" else "member",
    ) {
        val repository = FakeHouseholdRepository(household(canRestructure, role))
        val hierarchyStore =
            HierarchyStore(repository, EmptyLocations, EmptyShelves, EmptyProducts, Dispatchers.Main)
        val viewModel = HouseholdsViewModel(repository, hierarchyStore)
        composeRule.setContent {
            MaterialTheme {
                HouseholdEditScreen(householdId = 1, viewModel = viewModel)
            }
        }
    }

    @Test
    fun a_member_without_restructure_sees_no_rename_field_save_button_or_theme_swatches() {
        render(canRestructure = false)

        composeRule.onNodeWithTag("household-name-field").assertDoesNotExist()
        composeRule.onNodeWithTag("household-save-name").assertDoesNotExist()
        composeRule.onNodeWithTag("theme-color-sky").assertDoesNotExist()
        composeRule.onNodeWithTag("theme-icon-home").assertDoesNotExist()

        composeRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun an_admin_with_restructure_sees_the_rename_field_save_button_and_theme_swatches() {
        render(canRestructure = true)

        composeRule.onNodeWithTag("household-name-field").assertIsDisplayed()
        composeRule.onNodeWithTag("household-save-name").assertIsDisplayed()
        composeRule.onNodeWithTag("theme-color-sky").assertIsDisplayed()
        composeRule.onNodeWithTag("theme-icon-home").assertIsDisplayed()

        composeRule.onNodeWithText("Garage").assertIsDisplayed()
    }

    @Test
    fun an_owner_tapping_leave_sees_the_transfer_ownership_dialog_not_the_leave_confirm() {
        render(canRestructure = true, role = "owner")

        composeRule.onNodeWithText("Leave").performClick()

        composeRule.onNodeWithText("You're the owner").assertIsDisplayed()
        composeRule.onNodeWithText("Open members").assertIsDisplayed()
        composeRule.onNodeWithText("Leave Garage?").assertDoesNotExist()
    }

    @Test
    fun a_non_owner_tapping_leave_sees_the_normal_leave_confirm_dialog() {
        render(canRestructure = false, role = "member")

        composeRule.onNodeWithText("Leave").performClick()

        composeRule.onNodeWithText("Leave Garage?").assertIsDisplayed()
    }

    @Test
    fun an_owner_sees_the_delete_household_button() {
        render(canRestructure = true, role = "owner")

        composeRule.onNodeWithTag("household-delete-button").assertIsDisplayed()
    }

    @Test
    fun a_non_owner_does_not_see_the_delete_household_button() {
        render(canRestructure = true, role = "admin")

        composeRule.onNodeWithTag("household-delete-button").assertDoesNotExist()
    }

    @Test
    fun the_delete_dialogs_confirm_button_stays_disabled_until_the_exact_name_is_typed() {
        render(canRestructure = true, role = "owner")
        composeRule.onNodeWithTag("household-delete-button").performClick()

        composeRule.onNodeWithTag("household-delete-confirm-button").assertIsNotEnabled()

        composeRule.onNodeWithTag("household-delete-confirm-field").performTextInput("Gara")
        composeRule.onNodeWithTag("household-delete-confirm-button").assertIsNotEnabled()

        composeRule.onNodeWithTag("household-delete-confirm-field").performTextInput("ge")
        composeRule.onNodeWithTag("household-delete-confirm-button").assertIsEnabled()
    }

    @Test
    fun a_member_without_restructure_sees_the_readonly_role_hint() {
        render(canRestructure = false, role = "member")

        composeRule.onNodeWithText("Only the owner and admins can edit this household.").assertIsDisplayed()
    }

    @Test
    fun an_admin_with_restructure_does_not_see_the_readonly_role_hint() {
        render(canRestructure = true, role = "admin")

        composeRule.onNodeWithText("Only the owner and admins can edit this household.").assertDoesNotExist()
    }
}
