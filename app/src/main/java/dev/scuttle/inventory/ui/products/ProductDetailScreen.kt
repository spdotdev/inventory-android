package dev.scuttle.inventory.ui.products

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.scuttle.inventory.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val product = state.product

    // Navigate back when saved or deleted
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    // Local edit fields — re-seed when product loads from null
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name ?: "") }
    var description by rememberSaveable(product?.id) { mutableStateOf(product?.description ?: "") }
    var code by rememberSaveable(product?.id) { mutableStateOf(product?.code ?: "") }
    var isMandatory by rememberSaveable(product?.id) { mutableStateOf(product?.is_mandatory ?: false) }
    var localImageUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Re-seed fields once the product loads
    LaunchedEffect(product?.id) {
        product?.let {
            name = it.name
            description = it.description ?: ""
            code = it.code ?: ""
            isMandatory = it.is_mandatory ?: false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { localImageUri = it }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { _ -> }

    val statusBarInsets = WindowInsets.statusBars
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = {
            TopAppBar(
                windowInsets = statusBarInsets,
                title = { Text(product?.name ?: stringResource(R.string.product_detail_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.save(
                                name.trim(),
                                description.takeIf { it.isNotBlank() },
                                code.takeIf { it.isNotBlank() },
                                isMandatory,
                            )
                        },
                        enabled = name.isNotBlank() && !state.loading,
                    ) { Text(stringResource(R.string.product_detail_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Image section
            Box(
                modifier = Modifier
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
                Button(onClick = { cameraLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
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
                    Text(stringResource(R.string.product_detail_mandatory_label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.product_detail_mandatory_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isMandatory, onCheckedChange = { isMandatory = it })
            }

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
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_dialog_product_title, product?.name ?: "")) },
            text = { Text(stringResource(R.string.delete_dialog_product_text)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.delete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
