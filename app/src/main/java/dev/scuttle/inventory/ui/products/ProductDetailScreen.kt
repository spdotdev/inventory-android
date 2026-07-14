package dev.scuttle.inventory.ui.products

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.scuttle.inventory.R
import dev.scuttle.inventory.data.product.ProductEdit
import dev.scuttle.inventory.ui.common.SnackbarErrorEffect
import dev.scuttle.inventory.ui.hierarchy.UndoOutcome
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/**
 * Distinct from the plain product-name text, which can still be composed in
 * a previous back-stack destination during a navigation transition (e.g. a
 * search result card showing the same name) — tests must target this tag.
 */
const val PRODUCT_DETAIL_TITLE_TEST_TAG = "product_detail_title"

// Bounds typed low-stock thresholds well under the API cap (1,000,000).
private const val MAX_THRESHOLD_DIGITS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val product = state.product
    val context = LocalContext.current

    // Navigate back when saved
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // Local edit fields — re-seed when product loads from null
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name ?: "") }
    var description by rememberSaveable(product?.id) { mutableStateOf(product?.description ?: "") }
    var code by rememberSaveable(product?.id) { mutableStateOf(product?.code ?: "") }
    var isMandatory by rememberSaveable(product?.id) { mutableStateOf(product?.is_mandatory ?: false) }
    // Kept as text so the field can be empty (= threshold off); parsed on save.
    var lowStockThreshold by rememberSaveable(product?.id) {
        mutableStateOf(product?.low_stock_threshold?.toString() ?: "")
    }
    var localImageUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    // Re-seed fields once the product loads
    LaunchedEffect(product?.id) {
        product?.let {
            name = it.name
            description = it.description ?: ""
            code = it.code ?: ""
            isMandatory = it.is_mandatory ?: false
            lowStockThreshold = it.low_stock_threshold?.toString() ?: ""
        }
    }

    // Camera URI stored in a ref so it's available when TakePicture returns
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                localImageUri = uri
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                viewModel.uploadImage(uri, mime)
            }
        }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                cameraImageUri?.let { uri ->
                    localImageUri = uri
                    viewModel.uploadImage(uri, "image/jpeg")
                }
            }
        }

    // One-shot action errors (save/upload/delete) surface as a transient Snackbar,
    // then are consumed so they don't re-announce; load errors still let the user
    // pull-to-refresh to retry.
    val snackbarHostState = remember { SnackbarHostState() }
    SnackbarErrorEffect(state.error, snackbarHostState, onConsumed = viewModel::consumeError)

    // Delete keeps its confirm dialog below; this is the Undo ON TOP of it. The
    // screen navigates away on a successful delete (unlike Shelves/Storage/Drawer,
    // which stay put), so the "deleted, [Undo]" snackbar — and, if tapped, the
    // restore call's own outcome snackbar — must be shown and resolved HERE,
    // sequentially, before onBack() fires. Firing onBack() first would dispose
    // this screen (and cancel viewModel.undoDelete()'s coroutine, which runs in
    // the ViewModel's own scope) mid-flight — same hazard HouseholdsUiState.left's
    // doc comment describes for leave().
    val undoLabel = stringResource(R.string.delete_undo)
    val productDeletedMessage = stringResource(R.string.product_deleted)
    val undoneMessage = stringResource(R.string.delete_undone)
    val undoFailedMessage = stringResource(R.string.delete_undo_failed)
    LaunchedEffect(state.deleted) {
        if (!state.deleted) return@LaunchedEffect
        val batchId = state.lastBatchId
        if (batchId != null) {
            val result =
                snackbarHostState.showSnackbar(
                    message = productDeletedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
                val outcome = viewModel.state.first { it.undoResult != null }.undoResult
                snackbarHostState.showSnackbar(
                    if (outcome == UndoOutcome.SUCCESS) undoneMessage else undoFailedMessage,
                )
                viewModel.consumeUndoResult()
            } else {
                viewModel.consumeLastBatch()
            }
        }
        onBack()
    }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.testTag("product_detail_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = {
                    Text(
                        product?.name ?: stringResource(R.string.product_detail_title_fallback),
                        modifier = Modifier.testTag(PRODUCT_DETAIL_TITLE_TEST_TAG),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save(
                                ProductEdit(
                                    name = name.trim(),
                                    description = description.takeIf { it.isNotBlank() },
                                    code = code.takeIf { it.isNotBlank() },
                                    isMandatory = isMandatory,
                                    lowStockThreshold = lowStockThreshold.toIntOrNull()?.takeIf { it > 0 },
                                ),
                            )
                        },
                        enabled = name.isNotBlank() && !state.loading,
                    ) { Text(stringResource(R.string.product_detail_save)) }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::load,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                // Errors are shown via the Scaffold's Snackbar (SnackbarErrorEffect above).

                // Image section
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageSource: Any? = localImageUri ?: product?.image_url
                    if (imageSource != null) {
                        AsyncImage(
                            model = imageSource,
                            contentDescription = stringResource(R.string.product_detail_image_cd),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.product_detail_gallery))
                    }
                    Button(
                        onClick = {
                            val uri = createCameraUri(context)
                            cameraImageUri = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.product_detail_camera))
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(50) },
                    label = { Text(stringResource(R.string.product_detail_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.product_detail_field_description)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text(stringResource(R.string.product_detail_field_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.product_detail_mandatory_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.product_detail_mandatory_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isMandatory, onCheckedChange = { isMandatory = it })
                }

                OutlinedTextField(
                    value = lowStockThreshold,
                    onValueChange = { lowStockThreshold = it.filter(Char::isDigit).take(MAX_THRESHOLD_DIGITS) },
                    label = { Text(stringResource(R.string.product_detail_field_low_stock)) },
                    supportingText = { Text(stringResource(R.string.product_detail_low_stock_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.loading,
                ) {
                    Text(stringResource(R.string.product_detail_delete_button))
                }

                Spacer(Modifier.height(24.dp))
            }
        } // end PullToRefreshBox
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_dialog_product_title, product?.name ?: "")) },
            text = { Text(stringResource(R.string.delete_dialog_product_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
