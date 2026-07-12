package dev.scuttle.inventory.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.scuttle.inventory.data.LocationStats
import dev.scuttle.inventory.ui.theme.FrostCard
import dev.scuttle.inventory.ui.theme.HouseholdAvatar
import dev.scuttle.inventory.ui.theme.householdBarAccent

/**
 * "Products by location", grouped into one card per household (#33).
 *
 * With a single household this collapses to exactly what it was before: one card,
 * no header, bars in the theme accent.
 */
@Composable
fun DashboardLocationChart(
    groups: List<HouseholdLocationGroup>,
    maxProductCount: Int,
    showHouseholdHeaders: Boolean,
    onOpenLocation: (householdId: Long, locationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            val barColor = householdBarAccent(group.household.id, group.household.color)
            FrostCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showHouseholdHeaders) {
                        HouseholdHeader(group, barColor)
                    }
                    group.stats.forEach { stat ->
                        LocationBar(
                            stat = stat,
                            maxProductCount = maxProductCount,
                            barColor = if (showHouseholdHeaders) barColor else MaterialTheme.colorScheme.primary,
                            onClick = { onOpenLocation(stat.householdId, stat.location.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The household's avatar and name. The name is what actually identifies the group —
 * the accent only reinforces it, so the card still reads for a colour-blind user.
 */
@Composable
private fun HouseholdHeader(
    group: HouseholdLocationGroup,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HouseholdAvatar(
            householdId = group.household.id,
            colorKey = group.household.color,
            iconKey = group.household.icon,
            size = 28.dp,
        )
        Text(
            group.household.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .background(accent, MaterialTheme.shapes.extraSmall),
        )
    }
}

@Composable
private fun LocationBar(
    stat: LocationStats,
    maxProductCount: Int,
    barColor: Color,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stat.location.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall),
            ) {
                // Scaled against the global max, so bars stay comparable across households.
                val fraction =
                    if (maxProductCount > 0) stat.productCount.toFloat() / maxProductCount else 0f
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(12.dp)
                            .background(barColor, MaterialTheme.shapes.extraSmall),
                )
            }
            Text(
                stat.productCount.toString(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.width(24.dp),
            )
        }
    }
}
