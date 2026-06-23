package dev.scuttle.inventory.ui.invite

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.scuttle.inventory.ui.theme.SpaceMono

@Composable
fun InviteScreen(
    householdId: Long,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(householdId) {
        viewModel.load(householdId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("← Storage")
        }

        Text(text = "Invite to household")

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let { Text(text = it) }

        if (state.code.isNotEmpty()) {
            Text(text = "Join code")
            Text(text = state.code, fontFamily = SpaceMono)

            val qr = remember(state.link) { qrBitmap(state.link) }
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "Invite QR code",
                    modifier = Modifier.size(220.dp),
                )
            }

            Button(
                onClick = { clipboard.setText(AnnotatedString(state.link)) },
                enabled = state.link.isNotEmpty(),
            ) {
                Text("Copy link")
            }
        }
    }
}

private fun qrBitmap(content: String, size: Int = 512): Bitmap? {
    if (content.isEmpty()) return null
    return runCatching {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}
