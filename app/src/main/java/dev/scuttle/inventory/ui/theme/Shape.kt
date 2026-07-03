package dev.scuttle.inventory.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// B·Frost design uses generously rounded controls (D-021).
val FrostShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // chips, small badges
    small = RoundedCornerShape(10.dp),       // text fields, snackbars
    medium = RoundedCornerShape(16.dp),      // cards, dialogs
    large = RoundedCornerShape(20.dp),       // bottom sheets (top corners only via sheet)
    extraLarge = RoundedCornerShape(28.dp),  // FAB, full-screen panels
)
