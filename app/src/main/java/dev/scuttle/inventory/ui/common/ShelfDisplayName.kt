package dev.scuttle.inventory.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.dto.ShelfDto

/**
 * A shelf's display name: the localized "Unsorted" label for the system shelf
 * (ShelfDto.is_system — the server always stores the literal name "Unsorted", and
 * ShelfDto.name's own doc comment already promises "the client localises the label
 * off this" — final review, ALSO FIX: it never actually did until this), the raw
 * server [name] for every other shelf. Takes the raw fields rather than a [ShelfDto]
 * so ProductsViewModel's own `MoveTarget` (built outside any Composable, so it can't
 * localize anything itself) can still be displayed correctly at render time — see the
 * [ShelfDto] overload below for the common case.
 */
@Composable
fun shelfDisplayName(
    name: String,
    isSystem: Boolean,
): String = shelfDisplayNameRes(isSystem)?.let { stringResource(it) } ?: name

@Composable
fun shelfDisplayName(shelf: ShelfDto): String = shelfDisplayName(shelf.name, shelf.is_system)

/**
 * The string resource for a system shelf's localized label, or null for a normal
 * shelf (the composable above then falls back to the raw server name). Split out so
 * the mapping itself is unit-testable on the JVM, mirroring storageTypeLabelRes.
 */
@StringRes
fun shelfDisplayNameRes(isSystem: Boolean): Int? = if (isSystem) R.string.shelf_unsorted else null
