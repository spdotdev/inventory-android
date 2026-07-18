package dev.scuttle.inventory.ui.scanner

/**
 * Which caller opened the scanner (see [dev.scuttle.inventory.ScannerMode] in
 * MainActivity) — purely a display concern here: [LOOKUP] scans search across
 * every shelf, [ADD] scans add/increment stock on the shelf the caller opened
 * this screen from. Duplicated as a small local enum (rather than importing
 * MainActivity's) so this composable stays previewable/testable without the
 * activity's nav graph; MainActivity maps its own ScannerMode to this one.
 */
enum class ScannerDisplayMode { LOOKUP, ADD }
