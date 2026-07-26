package dev.scuttle.inventory.ui.scanner

/**
 * Which caller opened the scanner (see [dev.scuttle.inventory.ScannerMode] in
 * MainActivity) — purely a display concern here: [LOOKUP] scans search across
 * every shelf, [ADD] scans add/increment stock on the shelf the caller opened
 * this screen from, [JOIN] scans a household invite QR (opened from
 * Households, replacing the old ZXing default-activity scanner there so both
 * flows share this one camera screen — see MainActivity's ScannerMode.JOIN).
 * Duplicated as a small local enum (rather than importing MainActivity's) so
 * this composable stays previewable/testable without the activity's nav
 * graph; MainActivity maps its own ScannerMode to this one.
 */
enum class ScannerDisplayMode { LOOKUP, ADD, JOIN }
