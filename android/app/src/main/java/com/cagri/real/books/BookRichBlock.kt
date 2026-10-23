package com.cagri.real.books

import com.cagri.real.i18n.t

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import coil.compose.AsyncImage
import java.io.File

/** Preserve EPUB figures and cell boundaries without loading active HTML. */
@Composable
internal fun BookRichBlock(raw: String, style: TextStyle) {
    val data = remember(raw) { BookRichContent.decode(raw) } ?: return
    when (data.optString("kind")) {
        "image" -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            AsyncImage(model = File(data.getString("path")), contentDescription = data.optString("alt").ifBlank { t("Kitap görseli") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 500.dp), contentScale = ContentScale.Fit)
            data.optString("alt").takeIf { it.isNotBlank() }?.let { Text(it, style = style, modifier = Modifier.padding(top = 8.dp)) }
        }
        "table" -> {
            val rows = data.getJSONArray("rows")
            Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                repeat(rows.length()) { i ->
                    val cells = rows.getJSONArray(i)
                    Row { repeat(cells.length()) { j ->
                        Text(cells.getString(j), style = style, modifier = Modifier.width(180.dp).padding(8.dp))
                    } }
                }
            }
        }
    }
}
