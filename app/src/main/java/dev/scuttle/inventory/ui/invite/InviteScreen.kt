package dev.scuttle.inventory.ui.invite

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.LiveStatusText
import dev.scuttle.inventory.ui.theme.SpaceMono
import kotlinx.coroutines.delay

@Composable
fun InviteScreen(
    householdId: Long,
    storageName: String = "",
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(householdId) {
        viewModel.load(householdId)
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.search_back_button))
        }

        Text(text = stringResource(R.string.invite_title))

        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        state.error?.let {
            LiveStatusText(it)
        }

        if (state.code.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (storageName.isNotEmpty()) {
                    Text(
                        text = storageName,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = stringResource(R.string.invite_join_code_label),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = state.code,
                    fontFamily = SpaceMono,
                    textAlign = TextAlign.Center,
                )

                val qr = remember(state.link) { qrBitmap(state.link) }
                if (qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = stringResource(R.string.invite_qr_cd),
                        modifier = Modifier.size(220.dp),
                    )
                }

                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.link))
                        copied = true
                    },
                    enabled = state.link.isNotEmpty(),
                ) {
                    Text(if (copied) stringResource(R.string.invite_copied) else stringResource(R.string.invite_copy_link))
                }
            }
        }
    }
}

private fun qrBitmap(content: String, size: Int = 512): Bitmap? {
    if (content.isEmpty()) return null
    if (!android.util.Patterns.WEB_URL.matcher(content).matches()) return null
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
