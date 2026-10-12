package com.cagri.real.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Deliberately non-inline composition boundaries for the large detail screen.
 * Box/Column are inline: calling them directly merges the nested UI into the
 * enclosing JVM method. These boundaries keep the content lambdas in separate
 * methods, avoiding MethodTooLargeException without adding layout nodes.
 *
 * Keep the same receivers and default layout policies as the original calls.
 * Do not mark these functions inline.
 */
@Composable
internal fun DetailLayoutBox(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier, content = content)
}

@Composable
internal fun DetailLayoutColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, content = content)
}
