package com.levanyon.meditakip.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owns only this screen's use cases. Binding releases the camera on stop; disposal closes ML Kit. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScanner(modifier: Modifier, torch: Boolean, decoded: (String) -> Unit,
    flashAvailable: (Boolean) -> Unit, ready: () -> Unit, failed: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val onDecoded by rememberUpdatedState(decoded)
    val onFailure by rememberUpdatedState(failed)
    val onReady by rememberUpdatedState(ready)
    val onFlash by rememberUpdatedState(flashAvailable)
    val previewView = remember(context) { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var camera by remember { mutableStateOf<Camera?>(null) }
    AndroidView(factory = { previewView }, modifier = modifier)
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }
    DisposableEffect(owner, previewView) {
        val active = AtomicBoolean(true)
        val main = ContextCompat.getMainExecutor(context)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_EAN_13).build())
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        analysis.setAnalyzer(executor) { frame ->
            val media = frame.image
            if (!active.get() || media == null) frame.close()
            else try {
                scanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                    .addOnSuccessListener(main) { codes ->
                        if (active.get()) codes.firstOrNull { it.rawValue != null }?.rawValue?.let(onDecoded)
                    }
                    .addOnFailureListener(main) { if (active.get()) onFailure() }
                    .addOnCompleteListener { frame.close() }
            } catch (_: Exception) { frame.close(); main.execute { if (active.get()) onFailure() } }
        }
        providerFuture.addListener({
            if (active.get()) try {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) error("Permission revoked")
                val p = providerFuture.get(); provider = p
                val selector = when {
                    p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> error("No camera")
                }
                camera = p.bindToLifecycle(owner, selector, preview, analysis)
                onFlash(camera?.cameraInfo?.hasFlashUnit() == true); onReady()
            } catch (_: Exception) { onFailure() }
        }, main)
        onDispose {
            active.set(false)
            camera?.cameraControl?.enableTorch(false)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            scanner.close(); executor.shutdown(); camera = null
        }
    }
}
