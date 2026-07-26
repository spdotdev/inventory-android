package dev.scuttle.inventory.ui.settings

import dev.scuttle.inventory.R

/**
 * A fixed set of choices for the free-text `gender` field the server stores (see
 * UpdateProfileRequest in inventory-laravel — deliberately no server-side enum, so this
 * is a CLIENT-side convenience list, not a contract). [value] is the literal string sent
 * to/received from the API; [labelRes] is resolved via stringResource() by the screen so
 * the picker is localized without changing what's actually stored.
 */
enum class GenderOption(
    val value: String,
    val labelRes: Int,
) {
    FEMALE("female", R.string.account_gender_option_female),
    MALE("male", R.string.account_gender_option_male),
    PREFER_NOT_TO_SAY("prefer_not_to_say", R.string.account_gender_option_prefer_not_to_say),
    ;

    companion object {
        fun fromValue(value: String): GenderOption? = entries.find { it.value == value }
    }
}
