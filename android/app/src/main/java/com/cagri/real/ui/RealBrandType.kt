package com.cagri.real.ui

import android.content.Context
import android.graphics.Typeface
import com.cagri.real.R

/** The logo's typeface (Inter Tight Bold) for canvas drawings that have no Compose context, e.g. poster signatures. */
object RealBrandType {
    var bold: Typeface = Typeface.DEFAULT_BOLD
        private set

    fun init(context: Context) {
        runCatching { bold = context.resources.getFont(R.font.inter_tight_bold) }
    }
}
