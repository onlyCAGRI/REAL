package com.cagri.real.books

import com.cagri.real.i18n.t
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Referans okuyucu: Inter benzeri sans, 16sp, 1.5 satır aralığı, iki yana yaslı. */
object ReaderDefaults {
    const val FONT_SP = 16
    const val MIN_SP = 12
    const val MAX_SP = 28
    const val LINE_HEIGHT = 1.5f
}

/**
 * res/font içine inter_regular / inter_medium / inter_semibold / inter_bold (.ttf) koyarsan otomatik kullanılır.
 * Dosya yoksa derleme bozulmaz, sistem sans fontuna düşer.
 */
@Composable
fun rememberReaderFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        val fonts = listOf(
            "inter_regular" to FontWeight.Normal, "inter_medium" to FontWeight.Medium,
            "inter_semibold" to FontWeight.SemiBold, "inter_bold" to FontWeight.Bold,
        ).mapNotNull { (name, weight) ->
            val id = context.resources.getIdentifier(name, "font", context.packageName)
            if (id != 0) Font(id, weight) else null
        }
        if (fonts.isEmpty()) FontFamily.SansSerif else FontFamily(fonts)
    }
}

/** Okuyucu yazı tipi ("Tт" sırasından seçilir, bütün kitaplarda geçerli). */
enum class ReaderFont(val label: String) { SANS("Inter"), SERIF("Serif"), MONO("Mono") }

@Composable
fun rememberReaderFamily(font: ReaderFont): FontFamily {
    val sans = rememberReaderFamily()
    return when (font) {
        ReaderFont.SANS -> sans
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.MONO -> FontFamily.Monospace
    }
}

/** Dikey: aşağı doğru tek akış. Yatay: sayfalar yana kayar (yalnız elle kaydırma, dokunma ile geçiş yok). */
enum class ReadMode { VERTICAL, HORIZONTAL }
