package com.cagri.real.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private val DialogShape = RoundedCornerShape(28.dp)

/**
 * Material's AlertDialog with REAL's defaults: the same dark ink, 28dp corners, no tonal tint and
 * soft-white text for every confirmation. Same parameters, so it is a drop-in replacement.
 */
@Composable
fun RealAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = DialogShape,
    containerColor: Color = Color(0xFF161618),
    iconContentColor: Color = Color(0xFFF3F3F5),
    titleContentColor: Color = Color(0xFFF3F3F5),
    textContentColor: Color = Color(0xFFA9A9B2),
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties()
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = confirmButton,
    modifier = modifier,
    dismissButton = dismissButton,
    icon = icon,
    title = title,
    text = text,
    shape = shape,
    containerColor = containerColor,
    iconContentColor = iconContentColor,
    titleContentColor = titleContentColor,
    textContentColor = textContentColor,
    tonalElevation = tonalElevation,
    properties = properties
)
