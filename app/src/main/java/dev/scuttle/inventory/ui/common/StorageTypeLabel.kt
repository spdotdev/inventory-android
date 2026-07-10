package dev.scuttle.inventory.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.scuttle.inventory.R

/**
 * Maps a storage-location `type` transport value (freezer|fridge|pantry|other) to a
 * localized display label. The raw enum stays on the wire (server-authoritative);
 * only presentation is localized, so a Dutch device shows "Vriezer" not "freezer".
 * Unknown / future server values fall back to the capitalized raw string, so a new
 * type never renders blank before the app catches up.
 */
@Composable
fun storageTypeLabel(type: String): String =
    storageTypeLabelRes(type)?.let { stringResource(it) }
        ?: type.replaceFirstChar { it.uppercase() }

/**
 * The string resource for a known storage type, or null for an unknown/future value
 * (the composable then falls back to the capitalized raw string). Split out from the
 * composable above so the transport→resource mapping is unit-testable on the JVM.
 */
@StringRes
fun storageTypeLabelRes(type: String): Int? =
    when (type) {
        "freezer" -> R.string.storage_type_freezer
        "fridge" -> R.string.storage_type_fridge
        "pantry" -> R.string.storage_type_pantry
        "other" -> R.string.storage_type_other
        else -> null
    }
