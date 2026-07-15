package dev.scuttle.inventory.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.scuttle.inventory.R

// Google's official "Sign in with Google" button palette (light + dark variants).
// These are Google-mandated BRAND colours, not Frost theme tokens — swapping them
// for the app accent is exactly what makes a Google button stop reading as one.
private val GoogleLightContainer = Color(0xFFFFFFFF)
private val GoogleLightContent = Color(0xFF1F1F1F)
private val GoogleLightStroke = Color(0xFF747775)
private val GoogleDarkContainer = Color(0xFF131314)
private val GoogleDarkContent = Color(0xFFE3E3E3)
private val GoogleDarkStroke = Color(0xFF8E918F)

private val LogoSize = 20.dp
private val LogoGap = 12.dp
private val MinHeight = 52.dp
private val LabelSize = 15.sp

/**
 * "Continue with Google", styled to Google's Sign in with Google branding so the
 * affordance is recognisable at a glance: the four-colour G mark, a neutral
 * surface (white in light, near-black in dark) with a hairline border, and a
 * medium-weight label — not the app's accent-tinted button.
 *
 * The mark ([R.drawable.ic_google_g]) is drawn with [Color.Unspecified] tint so
 * its four brand colours survive; the surface follows the system light/dark mode
 * per Google's two approved variants. Keep both as-is — recolouring either breaks
 * the recognition this button exists to provide.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) GoogleDarkContainer else GoogleLightContainer
    val content = if (dark) GoogleDarkContent else GoogleLightContent
    val stroke = if (dark) GoogleDarkStroke else GoogleLightStroke

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = container,
                contentColor = content,
                // Keep the Google surface during the sign-in spinner (the button is
                // `enabled = false` while loading), rather than fading to Material's
                // default disabled grey.
                disabledContainerColor = container,
                disabledContentColor = content,
            ),
        border = BorderStroke(1.dp, stroke),
        modifier = modifier.fillMaxWidth().heightIn(min = MinHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = content,
                modifier = Modifier.size(LogoSize),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = null, // the label carries the meaning
                    tint = Color.Unspecified, // preserve the four brand colours
                    modifier = Modifier.size(LogoSize),
                )
                Text(
                    text = stringResource(R.string.auth_continue_with_google),
                    fontWeight = FontWeight.Medium,
                    fontSize = LabelSize,
                    modifier = Modifier.padding(start = LogoGap),
                )
            }
        }
    }
}
