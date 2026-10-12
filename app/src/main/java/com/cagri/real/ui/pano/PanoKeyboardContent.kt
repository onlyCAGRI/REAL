package com.cagri.real.ui.pano

import android.net.Uri
import android.view.inputmethod.InputContentInfo
import androidx.compose.runtime.staticCompositionLocalOf

/** Receiver owns the granted IME content until its bounded local copy completes. */
internal val LocalPanoKeyboardImage = staticCompositionLocalOf<((Uri, InputContentInfo?) -> Boolean)?> { null }
