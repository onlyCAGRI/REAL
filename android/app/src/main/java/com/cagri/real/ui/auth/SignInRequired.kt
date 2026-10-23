package com.cagri.real.ui.auth

import com.cagri.real.i18n.t
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cagri.real.ui.connections.ConnectionPalette
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Misafir: something that lives in the account (sharing, shared categories, suggestions) asks for one. The sheet is the
 * usual "Hesap ekle" one with a title for what was tapped; what the guest made here moves into the account on sign-in.
 */
object SignInRequired {
    enum class Reason(val title: String) {
        Share(t("Paylaşmak için hesap gerekiyor")),
        SharedCategory(t("Ortak kategori için hesap gerekiyor")),
        Suggestions(t("Öneriler için hesap gerekiyor")),
        Ratings(t("Puanlar için hesap gerekiyor")),
        Backup(t("Yedekleme için hesap gerekiyor")),
    }
    val KEEP_NOTE: String get() = t("Misafirken yaptıkların kaybolmaz; hesabına hemen taşınır ve buluta yedeklenir.")

    private val mutable = MutableStateFlow<Reason?>(null)
    val reason = mutable.asStateFlow()

    fun isGuest() = AndroidBackend.auth.currentUser()?.takeUnless { it.isAnonymous } == null

    /** True (and the sheet opens) when there is no account yet; false lets the caller go on. */
    fun needed(reason: Reason): Boolean {
        if (!isGuest()) return false
        mutable.value = reason
        return true
    }

    fun dismiss() { mutable.value = null }
}

@Composable
fun SignInRequiredHost() {
    val reason by SignInRequired.reason.collectAsState()
    reason?.let { AuthChoiceDialog(onDismiss = SignInRequired::dismiss, title = it.title, subtitle = SignInRequired.KEEP_NOTE) }
}

private const val NUDGE_AFTER_CARDS = 10
private const val NUDGE_LATER_MS = 7 * 24 * 60 * 60 * 1000L

/**
 * Hafif hatırlatma on Home: a guest with [cardCount] ≥ 10 cards sees it once; "Sonra" brings it back a week later one
 * more time, ✕ (or the second "Sonra") puts it away for good.
 */
@Composable
fun GuestBackupNudge(cardCount: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = androidx.compose.runtime.remember { context.getSharedPreferences("real_guest_nudge", android.content.Context.MODE_PRIVATE) }
    var shown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(
        !prefs.getBoolean("done", false) && System.currentTimeMillis() >= prefs.getLong("later_until", 0)) }
    if (!shown || cardCount < NUDGE_AFTER_CARDS || !SignInRequired.isGuest()) return
    fun away(forGood: Boolean) {
        val again = !forGood && !prefs.contains("later_until")
        prefs.edit().apply { if (again) putLong("later_until", System.currentTimeMillis() + NUDGE_LATER_MS) else putBoolean("done", true) }.apply()
        shown = false
    }
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF121A14))
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFF1F3A26), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
            Text(t("Kartların yalnız bu telefonda"), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(t("Telefon kaybolursa ya da uygulama silinirse gider. Hesap ekle, hepsi yedeklensin."),
                color = ConnectionPalette.Muted, fontSize = 12.sp, modifier = androidx.compose.ui.Modifier.padding(top = 3.dp, bottom = 10.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                ConnectionPill(t("Hesap ekle"), { SignInRequired.needed(SignInRequired.Reason.Backup) }, filled = true, small = true)
                ConnectionPill(t("Sonra"), { away(forGood = false) }, small = true)
            }
        }
        Text("✕", color = ConnectionPalette.Muted, fontSize = 14.sp,
            modifier = androidx.compose.ui.Modifier.clickable { away(forGood = true) }.padding(4.dp)
                .semantics { contentDescription = t("Hatırlatmayı kapat") })
    }
}
