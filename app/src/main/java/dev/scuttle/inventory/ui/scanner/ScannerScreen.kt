package dev.scuttle.inventory.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dev.scuttle.inventory.R
import dev.scuttle.inventory.ui.theme.Spacing
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen barcode scanner (Phase 2). CameraX preview + ML Kit on-device
 * detection; the first barcode with a raw value wins and is delivered once via
 * [onScanned]. The camera is an accelerator, never a requirement — when the
 * permission is denied the screen degrades to an explanation + back navigation.
 *
 * [mode] drives the title/subtitle only (GAP-5 H5): before this, the screen
 * showed one static title regardless of whether scanning here searches
 * globally (LOOKUP, from the bottom-bar Scan tab) or adds to a specific shelf
 * (ADD, opened from a shelf screen) — a scan landing "somewhere" with no
 * indication which was silently disorienting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    mode: ScannerDisplayMode = ScannerDisplayMode.ADD,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    // GAP-5 M7: the bound Camera is hoisted here so the top bar can toggle the
    // torch — scanning inside a dark fridge or cupboard is this app's home turf.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            permissionDenied = !granted
        }

    DisposableEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose {}
    }

    val title =
        when (mode) {
            ScannerDisplayMode.LOOKUP -> stringResource(R.string.scanner_title_lookup)
            ScannerDisplayMode.ADD -> stringResource(R.string.scanner_title_add)
            ScannerDisplayMode.JOIN -> stringResource(R.string.scanner_title_join)
        }
    val subtitle =
        when (mode) {
            ScannerDisplayMode.LOOKUP -> stringResource(R.string.scanner_subtitle_lookup)
            ScannerDisplayMode.ADD -> stringResource(R.string.scanner_subtitle_add)
            ScannerDisplayMode.JOIN -> stringResource(R.string.scanner_subtitle_join)
        }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
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
                    // Hidden entirely on devices without a flash unit.
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        IconButton(
                            onClick = {
                                torchOn = !torchOn
                                camera?.cameraControl?.enableTorch(torchOn)
                            },
                        ) {
                            Icon(
                                if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                contentDescription =
                                    stringResource(
                                        if (torchOn) R.string.scanner_torch_on else R.string.scanner_torch_off,
                                    ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                hasPermission -> {
                    CameraPreview(onScanned = onScanned, onCameraReady = { camera = it })
                    ScannerOverlay()
                }
                permissionDenied ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.scanner_permission_denied),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_back))
                        }
                        // GAP-5 M8: once denied, Android never re-prompts — the
                        // only way back is the app's settings page, so hand the
                        // user a direct route instead of a dead end.
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.scanner_open_settings))
                        }
                    }
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onScanned: (String) -> Unit,
    onCameraReady: (Camera) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Deliver at most one result: ML Kit keeps analyzing frames after the first
    // hit, and a double navigation crashes the back stack.
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().build())
            val analysis =
                ImageAnalysis
                    .Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
            analysis.setAnalyzer(executor) { imageProxy ->
                processFrame(scanner, imageProxy) { value ->
                    if (delivered.compareAndSet(false, true)) onScanned(value)
                }
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                preview.surfaceProvider = previewView.surfaceProvider
                provider.unbindAll()
                val boundCamera =
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                onCameraReady(boundCamera)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processFrame(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onValue: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner
        .process(input)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let(onValue)
        }.addOnCompleteListener { imageProxy.close() }
}

// Viewfinder proportions: frame side relative to the preview's short edge, and
// its vertical placement (fraction of the leftover height above the frame).
private const val FRAME_SIDE_FRACTION = 0.68f
private const val FRAME_VERTICAL_BIAS = 0.4f

// ZXing-red laser (Material Red 600).
private val LaserRed = Color(0xFFE53935)

// Length of each corner bracket arm relative to the frame side.
private const val BRACKET_ARM_FRACTION = 0.18f

// Soft-focus scrim: flat base dim plus a radial falloff toward the edges.
private const val SCRIM_BASE_ALPHA = 0.65f
private const val SCRIM_EDGE_ALPHA = 0.95f

/**
 * One twinkling dot near the scan line — [xFraction] positions it along the
 * line's width (0f = left inset, 1f = right inset); [yJitterFraction] offsets
 * it above/below the line (-1f..1f, scaled by [SPARKLE_JITTER_DP]); [phase] and
 * [speedMultiplier] desync its fade-in/fade-out cycle from every other dot so
 * they don't all twinkle in lockstep; [maxRadiusDp] varies dot size a little.
 */
private data class Sparkle(
    val xFraction: Float,
    val yJitterFraction: Float,
    val phase: Float,
    val speedMultiplier: Float,
    val maxRadiusDp: Float,
)

private const val SPARKLE_COUNT = 16
private const val SPARKLE_JITTER_DP = 10f
private const val SPARKLE_MIN_SPEED = 0.6f
private const val SPARKLE_SPEED_RANGE = 1.2f
private const val SPARKLE_MIN_RADIUS_DP = 1f
private const val SPARKLE_RADIUS_RANGE_DP = 1.8f
private const val SPARKLE_CYCLE_MS = 5000

// Fixed seed: the sparkle layout is stable across recompositions (each is
// `remember`ed once) but doesn't need to vary between screen opens either.
private const val SPARKLE_SEED = 20260726L
private val SPARKLE_TWO_PI = (2 * Math.PI).toFloat()
private const val SPARKLE_ALPHA_SCALE = 0.5f
private const val SPARKLE_MIN_VISIBLE_ALPHA = 0.05f
private const val SPARKLE_MAX_OPACITY = 0.85f

/**
 * Viewfinder overlay in the classic scan-frame style (four rounded corner
 * brackets, no full square — the standard "scanner overlay" vector look),
 * with a thin red line fixed across the middle of the frame plus small white
 * dots twinkling near it (fading in and out on staggered cycles) — this is
 * what "sparkly" meant on the old households ZXing scanner (its default
 * possible-result-point markers), which a flat pulsing line alone didn't
 * reproduce. Purely decorative — ML Kit analyzes the full frame — but it
 * tells the user where to point and that scanning is actively happening.
 */
@Composable
private fun ScannerOverlay() {
    val transition = rememberInfiniteTransition(label = "laser")
    // Kept translucent on purpose — the line guides without hiding the barcode.
    val laserAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "laser-alpha",
    )
    // 0f..1f, looping — each sparkle reads its own fade cycle off this via its
    // own phase/speed rather than needing SPARKLE_COUNT separate animations.
    val sparkleTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = SPARKLE_CYCLE_MS, easing = LinearEasing)),
        label = "sparkle-time",
    )
    val sparkles =
        remember {
            val random = kotlin.random.Random(SPARKLE_SEED)
            List(SPARKLE_COUNT) {
                Sparkle(
                    xFraction = random.nextFloat(),
                    yJitterFraction = random.nextFloat() * 2f - 1f,
                    phase = random.nextFloat(),
                    speedMultiplier = SPARKLE_MIN_SPEED + random.nextFloat() * SPARKLE_SPEED_RANGE,
                    maxRadiusDp = SPARKLE_MIN_RADIUS_DP + random.nextFloat() * SPARKLE_RADIUS_RANGE_DP,
                )
            }
        }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val side = size.minDimension * FRAME_SIDE_FRACTION
        val corner = CornerRadius(Spacing.lg.toPx())
        val frame =
            Rect(
                offset =
                    Offset(
                        x = (size.width - side) / 2f,
                        // Slightly above center — thumbs and the top bar both stay clear.
                        y = (size.height - side) * FRAME_VERTICAL_BIAS,
                    ),
                size = Size(side, side),
            )

        // Soft-focus scrim outside the frame (even-odd punch-out): a base dim
        // plus a radial falloff that is clear at the frame edge and darkens
        // outward, so the surroundings read as defocused and the eye settles
        // on the frame. (True camera blur would need a second GPU pass — the
        // preview surface bypasses Compose's blur.)
        val scrim =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(frame, corner))
            }
        drawPath(scrim, color = Color.Black.copy(alpha = SCRIM_BASE_ALPHA))
        drawPath(
            scrim,
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = SCRIM_EDGE_ALPHA),
                        ),
                    center = frame.center,
                    radius = size.maxDimension * 0.5f,
                ),
        )

        // Four rounded corner brackets instead of a full square.
        val arm = frame.width * BRACKET_ARM_FRACTION
        val r = corner.x
        val stroke =
            Stroke(
                width = Spacing.xs.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        val brackets =
            Path().apply {
                // Top-left
                moveTo(frame.left, frame.top + arm)
                lineTo(frame.left, frame.top + r)
                quadraticTo(frame.left, frame.top, frame.left + r, frame.top)
                lineTo(frame.left + arm, frame.top)
                // Top-right
                moveTo(frame.right - arm, frame.top)
                lineTo(frame.right - r, frame.top)
                quadraticTo(frame.right, frame.top, frame.right, frame.top + r)
                lineTo(frame.right, frame.top + arm)
                // Bottom-right
                moveTo(frame.right, frame.bottom - arm)
                lineTo(frame.right, frame.bottom - r)
                quadraticTo(frame.right, frame.bottom, frame.right - r, frame.bottom)
                lineTo(frame.right - arm, frame.bottom)
                // Bottom-left
                moveTo(frame.left + arm, frame.bottom)
                lineTo(frame.left + r, frame.bottom)
                quadraticTo(frame.left, frame.bottom, frame.left, frame.bottom - r)
                lineTo(frame.left, frame.bottom - arm)
            }
        drawPath(brackets, color = LaserRed, style = stroke)

        // Sweeping "sparkly" laser line: a bright core plus a soft glow band above
        // and below it (three strokes, widest = most transparent), traveling the
        // full frame height each half-cycle like ZXing's ViewfinderView.
        // Fixed center "laser" line with the traditional opacity pulse.
        val inset = 14.dp.toPx()
        val lineY = frame.top + frame.height / 2f
        drawLine(
            color = LaserRed.copy(alpha = laserAlpha),
            start = Offset(frame.left + inset, lineY),
            end = Offset(frame.right - inset, lineY),
            strokeWidth = 3.dp.toPx(),
        )

        // Twinkling dots scattered along the line, each fading in/out on its own
        // staggered sine cycle (phase + speedMultiplier) so they glitter rather
        // than pulse in unison.
        val jitterPx = SPARKLE_JITTER_DP.dp.toPx()
        val lineLeft = frame.left + inset
        val lineWidth = frame.right - inset - lineLeft
        sparkles.forEach { sparkle ->
            val cycle = (sparkleTime * sparkle.speedMultiplier + sparkle.phase) % 1f
            val sparkleAlpha = kotlin.math.sin(cycle * SPARKLE_TWO_PI) * SPARKLE_ALPHA_SCALE + SPARKLE_ALPHA_SCALE
            if (sparkleAlpha < SPARKLE_MIN_VISIBLE_ALPHA) return@forEach
            drawCircle(
                color = Color.White.copy(alpha = sparkleAlpha * SPARKLE_MAX_OPACITY),
                radius = sparkle.maxRadiusDp.dp.toPx() * sparkleAlpha,
                center =
                    Offset(
                        x = lineLeft + lineWidth * sparkle.xFraction,
                        y = lineY + sparkle.yJitterFraction * jitterPx,
                    ),
            )
        }
    }
}
