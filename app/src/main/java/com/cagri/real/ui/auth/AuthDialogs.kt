package com.cagri.real.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.auth.AuthCancelled
import com.cagri.real.auth.AuthUser
import com.cagri.real.auth.profileDisplayName
import com.cagri.real.ui.connections.ConnectionPalette
import com.cagri.real.ui.connections.ConnectionPill
import kotlinx.coroutines.launch

private enum class EmailAuthMode { SignIn, Create }

/** "Hesap ekle": Card Share ile aynı alt sayfa. Google / e-posta seçimi ve e-posta formu tek sheet içinde geçiş yapar. */
@Composable
fun AuthChoiceDialog(
    onDismiss: () -> Unit,
    onAuthenticated: (AuthUser) -> Unit = {}
) {
    val controller = LocalAuthUiController.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var emailBusy by remember { mutableStateOf(false) }
    var showEmail by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AuthSheet(canDismiss = { !busy && !emailBusy }, onDismiss = onDismiss) {
        AnimatedContent(showEmail, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "authPage") { email ->
            if (email) EmailAuthContent(
                onBack = { showEmail = false },
                onAuthenticated = { showEmail = false; onAuthenticated(it); onDismiss() },
                onBusy = { emailBusy = it }
            ) else Column(Modifier.fillMaxWidth().animateContentSize(tween(220)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthTitle("Hesap ekle", "Notların bu cihazda kalır. Hesap; eşitleme ve yedekleme için kullanılır.")
                Spacer(Modifier.height(6.dp))
                AuthMethodRow(AuthGlyph.Google, ConnectionPalette.Accent, if (busy) "Google açılıyor…" else "Google ile devam et",
                    "Hesabını Google ile bağla", enabled = !busy && controller != null) {
                    val active = controller ?: return@AuthMethodRow
                    busy = true
                    error = null
                    scope.launch {
                        val result = active.signInWithGoogle()
                        busy = false
                        result.onSuccess {
                            onAuthenticated(it)
                            onDismiss()
                        }.onFailure { throwable ->
                            if (throwable !is AuthCancelled) {
                                error = throwable.message ?: "Google ile giriş yapılamadı."
                            }
                        }
                    }
                }
                AuthMethodRow(AuthGlyph.Mail, Color(0xFFB59BFF), "E-posta ile devam et",
                    "Giriş yap ya da hesap oluştur", enabled = !busy && controller != null) {
                    error = null
                    showEmail = true
                }
                error?.let { AuthMessage(it, ConnectionPalette.Danger) }
                if (busy) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(4.dp))
                ConnectionPill("Vazgeç", { if (!busy) onDismiss() }, Modifier.fillMaxWidth(), enabled = !busy, small = true)
            }
        }
    }
}

@Composable
fun EmailAuthDialog(
    onDismiss: () -> Unit,
    onAuthenticated: (AuthUser) -> Unit = {}
) {
    var busy by remember { mutableStateOf(false) }
    AuthSheet(canDismiss = { !busy }, onDismiss = onDismiss) {
        EmailAuthContent(onBack = onDismiss, onAuthenticated = onAuthenticated, onBusy = { busy = it })
    }
}

@Composable
private fun EmailAuthContent(
    onBack: () -> Unit,
    onAuthenticated: (AuthUser) -> Unit,
    onBusy: (Boolean) -> Unit
) {
    val controller = LocalAuthUiController.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(EmailAuthMode.SignIn) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var createdUid by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(busy) { onBusy(busy) }

    fun submit() {
        if (busy) return
        val active = controller ?: run {
            error = "Hesap servisi hazır değil."
            return
        }
        val normalizedEmail = email.trim()
        if (createdUid == null && (normalizedEmail.isBlank() || password.isBlank())) {
            error = "E-posta ve şifreyi doldurun."
            return
        }
        if (mode == EmailAuthMode.Create) {
            try { profileDisplayName(firstName, lastName) }
            catch (failure: IllegalArgumentException) { error = failure.message; return }
            if (createdUid == null && password.length < 6) {
                error = "Şifre en az 6 karakter olmalı."
                return
            }
            if (createdUid == null && password != confirmPassword) {
                error = "Şifreler eşleşmiyor."
                return
            }
        }

        busy = true
        error = null
        info = null
        scope.launch {
            val result = if (mode == EmailAuthMode.SignIn) {
                active.signInWithEmail(normalizedEmail, password)
            } else {
                val uid = createdUid
                if (uid != null) active.updateProfileName(uid, firstName, lastName)
                else active.createAccountWithName(normalizedEmail, password, firstName, lastName)
            }
            busy = false
            result.onSuccess {
                onAuthenticated(it)
            }.onFailure {
                if (it is com.cagri.real.auth.AuthProfileFailure) createdUid = it.createdUid
                error = it.message ?: "İşlem tamamlanamadı."
            }
        }
    }

    Column(Modifier.fillMaxWidth().animateContentSize(tween(220)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { com.cagri.real.ui.components.RealBackButton(onBack, enabled = !busy) }
        Text(if (mode == EmailAuthMode.SignIn) "E-posta ile giriş" else "Hesap oluştur", color = Color.White,
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))

        if (mode == EmailAuthMode.Create) {
            AuthTextField(firstName, { if (!busy) { firstName = it; error = null } }, "Ad (zorunlu)", KeyboardType.Text, imeAction = ImeAction.Next)
            AuthTextField(lastName, { if (!busy) { lastName = it; error = null } }, "Soyad (isteğe bağlı)", KeyboardType.Text, imeAction = ImeAction.Next)
        }
        AuthTextField(
            value = email,
            onValueChange = { if (!busy && createdUid == null) { email = it; error = null; info = null } },
            placeholder = "E-posta",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        )
        AuthTextField(
            value = password,
            onValueChange = { password = it; error = null; info = null },
            placeholder = "Şifre",
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = if (mode == EmailAuthMode.Create) ImeAction.Next else ImeAction.Done,
            onDone = { if (mode == EmailAuthMode.SignIn) submit() }
        )
        if (mode == EmailAuthMode.Create) {
            AuthTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null; info = null },
                placeholder = "Şifreyi tekrar yaz",
                keyboardType = KeyboardType.Password,
                visualTransformation = PasswordVisualTransformation(),
                imeAction = ImeAction.Done,
                onDone = { submit() }
            )
        }

        error?.let { AuthMessage(it, ConnectionPalette.Danger) }
        info?.let { AuthMessage(it, Color(0xFF7BE0A4)) }

        Spacer(Modifier.height(4.dp))
        AuthActionButton(
            label = when {
                busy -> "Bekleyin…"
                createdUid != null -> "Adı kaydet ve devam et"
                mode == EmailAuthMode.SignIn -> "Giriş yap"
                else -> "Hesap oluştur"
            },
            enabled = !busy && controller != null,
            emphasized = true,
            onClick = { submit() }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mode == EmailAuthMode.SignIn) ConnectionPill("Şifremi unuttum", {
                val normalizedEmail = email.trim()
                if (normalizedEmail.isBlank()) {
                    error = "Önce e-posta adresini yazın."
                    return@ConnectionPill
                }
                val active = controller ?: return@ConnectionPill
                busy = true
                error = null
                info = null
                scope.launch {
                    val result = active.sendPasswordReset(normalizedEmail)
                    busy = false
                    result.onSuccess {
                        info = "Şifre sıfırlama bağlantısı e-posta adresine gönderildi."
                    }.onFailure {
                        error = it.message ?: "Sıfırlama e-postası gönderilemedi."
                    }
                }
            }, Modifier.weight(1f), enabled = !busy, small = true)
            ConnectionPill(if (mode == EmailAuthMode.SignIn) "Yeni hesap oluştur" else "Zaten hesabım var", {
                if (!busy && createdUid == null) {
                    mode = if (mode == EmailAuthMode.SignIn) EmailAuthMode.Create else EmailAuthMode.SignIn
                    password = ""
                    confirmPassword = ""
                    error = null
                    info = null
                }
            }, Modifier.weight(1f), enabled = !busy && createdUid == null, small = true)
        }
    }
}

@Composable
fun AccountManagementDialog(onDismiss: () -> Unit, onAddAccount: () -> Unit) {
    com.cagri.real.ui.account.AccountManagementSheet(onDismiss, onAddAccount)
}

/** Card Share / Hesapları yönet ile aynı sheet geometrisi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthSheet(canDismiss: () -> Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val allowed by rememberUpdatedState(canDismiss)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { allowed() })
    ModalBottomSheet(onDismissRequest = { if (allowed()) onDismiss() }, sheetState = state,
        containerColor = ConnectionPalette.Ink, contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(36.dp, 4.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = .22f))) }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 20.dp), content = content)
    }
}

@Composable
private fun AuthTitle(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Text(subtitle, color = ConnectionPalette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
}

private enum class AuthGlyph { Google, Mail }

@Composable
private fun AuthMethodRow(glyph: AuthGlyph, tone: Color, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f).clip(shape).background(ConnectionPalette.Glass)
        .border(1.dp, ConnectionPalette.Stroke, shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tone.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            when (glyph) {
                AuthGlyph.Google -> Text("G", color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                AuthGlyph.Mail -> Canvas(Modifier.size(20.dp)) {
                    val w = size.width; val h = size.height
                    val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    drawRoundRect(tone, Offset(w * .06f, h * .2f), Size(w * .88f, h * .62f), CornerRadius(w * .1f), style = stroke)
                    drawLine(tone, Offset(w * .10f, h * .28f), Offset(w * .5f, h * .58f), stroke.width, StrokeCap.Round)
                    drawLine(tone, Offset(w * .90f, h * .28f), Offset(w * .5f, h * .58f), stroke.width, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ConnectionPalette.Muted, fontSize = 13.sp)
        }
        Text("›", color = ConnectionPalette.Muted, fontSize = 20.sp)
    }
}

/** Beyaz dolu (emphasized) ya da cam hap düğme; ProfileNameForm da bunu kullanır. */
@Composable
private fun AuthActionButton(
    label: String,
    enabled: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    ConnectionPill(label, onClick, Modifier.fillMaxWidth(), enabled = enabled, filled = emphasized)
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction,
    onDone: () -> Unit = {}
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(ConnectionPalette.Glass)
            .border(1.dp, ConnectionPalette.Stroke, shape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isBlank()) {
            Text(placeholder, color = ConnectionPalette.Muted, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() })
        )
    }
}

@Composable
private fun AuthMessage(message: String, tone: Color) {
    Text(message, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = .10f))
        .padding(horizontal = 14.dp, vertical = 10.dp), color = tone, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun AuthErrorText(message: String) = AuthMessage(message, ConnectionPalette.Danger)

private fun providerLabel(providerIds: List<String>): String = providerIds.mapNotNull {
    when (it) {
        "google.com" -> "Google"
        "password" -> "E-posta ve şifre"
        "apple.com" -> "Apple"
        else -> null
    }
}.distinct().joinToString(" · ")

/** Used before Connections, Pano invitation acceptance and card sharing for legacy unnamed users. */
@Composable
fun ProfileNameForm(user: AuthUser, onSaved: (AuthUser) -> Unit) {
    val scope = rememberCoroutineScope()
    var first by remember(user.id) { mutableStateOf("") }
    var last by remember(user.id) { mutableStateOf("") }
    var busy by remember(user.id) { mutableStateOf(false) }
    var error by remember(user.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Text("REAL profilini tamamla", color = Color.White, fontSize = 20.sp)
        Text("Bağlantılarınız ve gönderdiğiniz kartlar bu isimle görünür.", color = Color(0xFFA9A9B2), fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        AuthTextField(first, { if (!busy) first = it }, "Ad (zorunlu)", KeyboardType.Text, imeAction = ImeAction.Next)
        Spacer(Modifier.height(10.dp))
        AuthTextField(last, { if (!busy) last = it }, "Soyad (isteğe bağlı)", KeyboardType.Text, imeAction = ImeAction.Done)
        Spacer(Modifier.height(10.dp))
        AuthActionButton(if (busy) "Kaydediliyor…" else "Kaydet ve devam et", !busy, emphasized = true) {
            busy = true; error = null
            scope.launch {
                try {
                    com.cagri.real.platform.android.auth.AndroidBackend.auth
                        .updateProfileName(user.id, first, last)
                        .onSuccess(onSaved).onFailure { error = it.message ?: "İsim kaydedilemedi." }
                } finally { busy = false }
            }
        }
        error?.let { AuthErrorText(it) }
    }
}
