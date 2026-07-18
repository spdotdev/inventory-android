package dev.scuttle.inventory.ui.invite

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.common.ErrorRetry
import dev.scuttle.inventory.ui.theme.SpaceMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

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
        modifier =
            modifier
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

        state.errorRes?.let {
            ErrorRetry(stringResource(it), onRetry = { viewModel.load(householdId) })
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

                // Generate the 512×512 QR off the composition thread — the setPixel
                // loop (~262k pixels) otherwise hitches the frame when the invite
                // loads (W22). Show a spinner in the QR's footprint until it's ready.
                val qr by produceState<Bitmap?>(null, state.link) {
                    value = withContext(Dispatchers.Default) { qrBitmap(state.link) }
                }
                val currentQr = qr
                when {
                    currentQr != null ->
                        Image(
                            bitmap = currentQr.asImageBitmap(),
                            contentDescription = stringResource(R.string.invite_qr_cd),
                            modifier = Modifier.size(220.dp),
                        )
                    state.link.isNotEmpty() ->
                        Box(
                            modifier = Modifier.size(220.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                }

                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(state.link))
                        copied = true
                    },
                    enabled = state.link.isNotEmpty(),
                ) {
                    Text(
                        if (copied) {
                            stringResource(
                                R.string.invite_copied,
                            )
                        } else {
                            stringResource(R.string.invite_copy_link)
                        },
                    )
                }
            }
        }
    }
}

private fun qrBitmap(
    content: String,
    size: Int = 512,
): Bitmap? {
    if (content.isEmpty()) return null
    if (!android.util.Patterns.WEB_URL
            .matcher(content)
            .matches()
    ) {
        return null
    }
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
