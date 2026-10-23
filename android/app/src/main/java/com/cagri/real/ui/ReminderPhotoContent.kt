package com.cagri.real.ui

import com.cagri.real.i18n.t
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.cagri.real.data.*
import com.cagri.real.ui.components.*
import java.io.File

private object AttachmentAspectRatios {
    private val values = object : LinkedHashMap<String, Float>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean = size > 64
    }
    @Synchronized fun get(path: String): Float? = values[path]
    @Synchronized fun put(path: String, ratio: Float) { values[path] = ratio }
}
private fun attachmentAspectRatio(path: String): Float {
    return runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else {
            1f
        }
    }.getOrDefault(1f)
}

@Composable
internal fun AttachmentMediaSection(
    attachments: List<ReminderAttachment>,
    designStyle: DesignStyle,
    editing: Boolean,
    onOpen: (ReminderAttachment) -> Unit,
    onRemove: (ReminderAttachment) -> Unit,
    heightOverride: Dp? = null
) {
    if (attachments.isEmpty()) return

    val sectionShape = RoundedCornerShape(22.dp)
    val sectionFill = if (designStyle == DesignStyle.LiquidGlass) {
        LiquidGlassTokens.contentColor
    } else {
        Color(0xFF141416)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (attachments.size) {
            1 -> {
                val attachment = attachments.first()
                val ratio by produceState(AttachmentAspectRatios.get(attachment.localPath) ?: 1f, attachment.localPath) {
                    value = AttachmentAspectRatios.get(attachment.localPath)
                        ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            attachmentAspectRatio(attachment.localPath).coerceIn(0.62f, 1.95f).also {
                                AttachmentAspectRatios.put(attachment.localPath, it)
                            }
                        }
                }
                val sizeModifier = when {
                    LocalCardContentComposition.current && heightOverride != null -> Modifier.fillMaxWidth().height(heightOverride.coerceAtLeast(80.dp))
                    LocalCardContentComposition.current -> Modifier.fillMaxWidth().aspectRatio(ratio)
                    ratio < 0.9f -> Modifier.fillMaxWidth(0.72f).aspectRatio(ratio)
                    ratio > 1.15f -> Modifier.fillMaxWidth().aspectRatio(ratio)
                    else -> Modifier.fillMaxWidth(0.82f).aspectRatio(1f)
                }
                Box(
                    modifier = sizeModifier
                        .clip(sectionShape)
                        .background(sectionFill)
                        .then(
                            if (LocalCardContentComposition.current) Modifier else if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier.border(0.75.dp, liquidContentEdge(), sectionShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0xFF2D2D33), sectionShape)
                            }
                        )
                        .padding(if (LocalCardContentComposition.current) 0.dp else 3.dp)
                ) {
                    AttachmentPhotoTile(
                        attachment = attachment,
                        editing = editing,
                        onOpen = onOpen,
                        onRemove = onRemove,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(19.dp)
                    )
                }
            }

            2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(sectionShape)
                        .background(sectionFill)
                        .then(
                            if (LocalCardContentComposition.current) Modifier else if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier.border(0.75.dp, liquidContentEdge(), sectionShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0xFF2D2D33), sectionShape)
                            }
                        )
                        .padding(if (LocalCardContentComposition.current) 0.dp else 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    attachments.forEach { attachment ->
                        AttachmentPhotoTile(
                            attachment = attachment,
                            editing = editing,
                            onOpen = onOpen,
                            onRemove = onRemove,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    }
                }
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(218.dp)
                        .clip(sectionShape)
                        .background(sectionFill)
                        .then(
                            if (LocalCardContentComposition.current) Modifier else if (designStyle == DesignStyle.LiquidGlass) {
                                Modifier.border(0.75.dp, liquidContentEdge(), sectionShape)
                            } else {
                                Modifier.border(0.75.dp, Color(0xFF2D2D33), sectionShape)
                            }
                        )
                        .padding(if (LocalCardContentComposition.current) 0.dp else 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    AttachmentPhotoTile(
                        attachment = attachments[0],
                        editing = editing,
                        onOpen = onOpen,
                        onRemove = onRemove,
                        modifier = Modifier.weight(1.18f).fillMaxHeight(),
                        shape = RoundedCornerShape(18.dp)
                    )
                    Column(
                        modifier = Modifier.weight(0.82f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        attachments.drop(1).take(2).forEach { attachment ->
                            AttachmentPhotoTile(
                                attachment = attachment,
                                editing = editing,
                                onOpen = onOpen,
                                onRemove = onRemove,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPhotoTile(
    attachment: ReminderAttachment,
    editing: Boolean,
    onOpen: (ReminderAttachment) -> Unit,
    onRemove: (ReminderAttachment) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp)
) {
    val zoom = remember(attachment.id, attachment.localPath) { CardPhotoZoomState() }
    val allowZoom = LocalCardContentComposition.current && !LocalCardBlockDragging.current
    Box(
        modifier = modifier
            .cardPhotoZoomInput(zoom, allowZoom)
            .clip(shape)
            .background(Color(0xFF101012))
            .clickable { onOpen(attachment) }
            .semantics { contentDescription = t("Fotoğrafı büyüt") }
    ) {
        AsyncImage(
            model = File(attachment.localPath),
            contentDescription = t("Fotoğraf eki"),
            modifier = Modifier.fillMaxSize().cardPhotoPixels(zoom),
            contentScale = ContentScale.Crop
        )
        val waiting by com.cagri.real.platform.android.sync.SyncLocal.waitingForSpace.collectAsState()
        if (attachment.localPath in waiting) CloudOffBadge(Modifier.align(Alignment.BottomStart).padding(6.dp))
        if (editing) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .padding(if (LocalCardContentComposition.current) 0.dp else 3.dp)
                    .clip(CircleShape)
                    .background(Color(0xD918181B))
                    .border(0.5.dp, Color(0x4DFFFFFF), CircleShape)
                    .clickable { onRemove(attachment) }
                    .semantics { contentDescription = t("Fotoğrafı kaldır") },
                contentAlignment = Alignment.Center
            ) {
                ActionIcon(ActionGlyph.Close, Modifier.size(11.dp), Color(0xFFF1F1F4))
            }
        }
    }
}

/** Bulut alanı dolu: this photo stays on the phone until there is room (then it goes up by itself). */
@Composable
private fun CloudOffBadge(modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp).clip(CircleShape).background(Color(0xD918181B)).padding(5.dp)
        .semantics { contentDescription = t("Bulut alanı dolu, fotoğraf bu telefonda") }) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width; val h = size.height
        val cloud = Path().apply {
            moveTo(w * .25f, h * .78f)
            cubicTo(w * .02f, h * .78f, w * .02f, h * .42f, w * .26f, h * .44f)
            cubicTo(w * .30f, h * .14f, w * .70f, h * .14f, w * .74f, h * .42f)
            cubicTo(w * .98f, h * .42f, w * .98f, h * .78f, w * .75f, h * .78f)
            close()
        }
        drawPath(cloud, Color(0xFFFFB86F), style = stroke)
        drawLine(Color(0xFFFFB86F), Offset(w * .1f, h * .1f), Offset(w * .9f, h * .9f), 1.4.dp.toPx(), StrokeCap.Round)
    }
}

/** The existing REAL photo preview, shared without copying its screen. */
@Composable
internal fun ReminderPhotoPreview(path: String, onDismiss: () -> Unit, topPadding: Dp = 0.dp, allowZoom: Boolean = false) {
            val zoom = remember(path) { CardPhotoZoomState() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape)
                    .cardPhotoZoomInput(zoom, allowZoom)
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = t("Fotoğraf tam ekran önizleme"),
                    modifier = Modifier.fillMaxSize().cardPhotoPixels(zoom),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { onDismiss() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = topPadding + 10.dp, start = 16.dp)
                        .background(Color(0x80161619), CircleShape)
                        .semantics { contentDescription = t("Önizlemeyi kapat") }
                ) {
                    ActionIcon(ActionGlyph.Close, Modifier.size(22.dp), Color.White)
                }
            }
}
