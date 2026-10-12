package com.cagri.real.ui.auth

import androidx.compose.runtime.staticCompositionLocalOf
import com.cagri.real.auth.AuthUiController

/** Authentication is optional so previews/tests can render without a platform identity stack. */
val LocalAuthUiController = staticCompositionLocalOf<AuthUiController?> { null }
