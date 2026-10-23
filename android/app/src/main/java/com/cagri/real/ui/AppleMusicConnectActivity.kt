package com.cagri.real.ui

import com.cagri.real.i18n.t
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cagri.real.MainActivity
import com.cagri.real.discovery.AppleMusicAccount

/** real://apple-music: the Apple Music sign-in page's answer. Stores the token, then back to REAL where it was. */
class AppleMusicConnectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ok = AppleMusicAccount.complete(this, intent.data)
        Toast.makeText(this, if (ok) t("Apple Music bağlandı") else t("Apple Music bağlantısı tamamlanmadı"), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
