package com.cagri.real.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.cagri.real.ui.motion.realEntrance
import com.cagri.real.ui.motion.rememberRealEntrance
import java.io.File
import java.util.UUID

/** Camera files remain private drafts until explicitly added through AttachmentStorage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardCameraSheet(reduceAnimations: Boolean, onDismiss: () -> Unit, onUsePhoto: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entrance = rememberRealEntrance(reduceAnimations, totalMs = 550)
    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(permitted()) }
    var requested by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        if (!it) error = "Kamera izni verilmedi. Fotoğraf eklemek için galeriyi de kullanabilirsin."
    }
    LaunchedEffect(Unit) {
        if (!permission) { requested = true; permissionLauncher.launch(Manifest.permission.CAMERA) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = permitted()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var front by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var canFlip by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<File?>(null) }
    var alive by remember { mutableStateOf(true) }
    var transferred by remember { mutableStateOf(false) }
    val previewView = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val capture = remember { ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setTargetResolution(Size(1600, 1200)).build() }
    val usePhoto by rememberUpdatedState(onUsePhoto)
    DisposableEffect(Unit) {
        onDispose {
            alive = false
            if (!transferred) photo?.delete()
            // An active CameraX write is deleted by its completion callback, not mid-write.
        }
    }
    DisposableEffect(permission, front, lifecycle, photo) {
        var active = true
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        cameraReady = false
        if (permission && photo == null) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active && alive) {
                    previewView.doOnLayout {
                        if (active && alive) runCatching {
                            val p = future.get()
                            provider = p
                            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            check(p.hasCamera(selector))
                            canFlip = p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                            val viewport = checkNotNull(previewView.viewPort)
                            val group = UseCaseGroup.Builder().setViewPort(viewport)
                                .addUseCase(preview).addUseCase(capture).build()
                            p.bindToLifecycle(lifecycle, selector, group)
                        }.onSuccess { cameraReady = true; error = null }
                            .onFailure { error = "Kamera açılamadı. Galeriden fotoğraf ekleyebilirsin." }
                    }
                }
            }, executor)
        }
        onDispose {
            active = false
            provider?.unbind(preview, capture)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet,
        containerColor = Color.Black, contentColor = Color.White,
        scrimColor = RealSheetDefaults.Scrim, shape = RealSheetDefaults.Shape,
        dragHandle = { RealSheetDefaults.DragHandle() }) {
        BoxWithConstraints(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)
                .realEntrance(entrance, durationMs = 400, slideY = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Fotoğraf", style = com.cagri.real.ui.theme.RealType.editorialSubtitle, modifier = Modifier.weight(1f))
                    CameraControl(ActionGlyph.Close, "Kapat", !busy, onDismiss)
                }
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF121214)),
                    contentAlignment = Alignment.Center) {
                    val shown = photo
                    if (shown != null) AsyncImage(shown, "Çekilen fotoğraf", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else if (permission) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ActionIcon(ActionGlyph.Camera, Modifier.size(36.dp), Color.White)
                        Text("Kamera için izin gerekiyor", modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
                        TextButton(onClick = {
                            if (requested) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")))
                            else { requested = true; permissionLauncher.launch(Manifest.permission.CAMERA) }
                        }) { Text("İzni aç", color = Color.White) }
                    }
                    if (permission && !cameraReady && shown == null && error == null) com.cagri.real.ui.components.RealSpinner(color = Color.White)
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp).height(68.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (photo != null) {
                            TextButton(enabled = !busy, onClick = { photo?.delete(); photo = null; error = null }) {
                                Text("Yeniden çek", color = Color.White)
                            }
                            Row(Modifier.clip(RoundedCornerShape(24.dp)).liquidChrome(24.dp)
                                .clickable(enabled = !busy) {
                                    val file = photo
                                    if (file != null && !transferred) { transferred = true; usePhoto(file) }
                                }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionIcon(ActionGlyph.Add, Modifier.size(18.dp), Color.White)
                                Text("Karta ekle", color = Color.White)
                            }
                        } else {
                            CameraControl(ActionGlyph.Repeat, "Kamerayı değiştir", canFlip && !busy) { front = !front }
                            Box(Modifier.size(68.dp).border(2.dp, Color.White, CircleShape).padding(5.dp)
                                .clip(CircleShape).background(if (cameraReady && !busy) Color.White else Color.DarkGray)
                                .clickable(enabled = cameraReady && !busy) {
                                    busy = true; error = null
                                    val dir = File(context.cacheDir, "real_camera").apply { mkdirs() }
                                    val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
                                    capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                                    try { capture.takePicture(ImageCapture.OutputFileOptions.Builder(file)
                                        .setMetadata(ImageCapture.Metadata().apply { isReversedHorizontal = front }).build(), executor,
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                                if (alive) { photo = file; busy = false } else file.delete()
                                            }
                                            override fun onError(exception: ImageCaptureException) {
                                                file.delete()
                                                if (alive) { busy = false; error = "Fotoğraf çekilemedi. Tekrar deneyebilirsin." }
                                            }
                                        }) } catch (_: Exception) {
                                        file.delete(); busy = false
                                        error = "Fotoğraf çekilemedi. Tekrar deneyebilirsin."
                                    }
                                }.semanticsCameraCapture(), contentAlignment = Alignment.Center) {
                                if (busy) com.cagri.real.ui.components.RealSpinner(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                            }
                            Spacer(Modifier.size(48.dp))
                        }
                    }
                }
                error?.let { Text(it, color = Color(0xFFB1B1BA), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

private fun Modifier.semanticsCameraCapture(): Modifier = this.then(Modifier.semantics {
    contentDescription = "Fotoğraf çek"
})

@Composable
private fun CameraControl(glyph: ActionGlyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).liquidChrome(24.dp)
        .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        ActionIcon(glyph, Modifier.size(22.dp), Color.White.copy(alpha = if (enabled) 1f else .35f))
    }
}
