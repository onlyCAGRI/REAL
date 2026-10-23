package com.cagri.real.ui.share

import com.cagri.real.i18n.t
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cagri.real.MainActivity
import com.cagri.real.platform.android.share.CardShareClient
import com.cagri.real.platform.android.sync.SyncLocal
import com.cagri.real.startup.StartupPhase
import com.cagri.real.ui.theme.RealTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Explicit web 'Add to REAL' handoff, isolated from MainActivity's startup/navigation state. */
class SharedCardImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = com.cagri.real.share.sharedCardIdFromLink(intent.dataString)
        if (id == null) { finish(); return }
        setContent {
            RealTheme {
                SharedCardImportScreen(id,
                    onSignIn = { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
                    onDone = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    }, onCancel = ::finish)
            }
        }
    }
}

@Composable
private fun SharedCardImportScreen(id: String, onSignIn: () -> Unit, onDone: () -> Unit, onCancel: () -> Unit) {
    val startup by SyncLocal.startup.state.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var claimed by androidx.compose.runtime.saveable.rememberSaveable(id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("REAL", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(22.dp))
        Text("Add shared card", color = Color.White)
        Text("Save an independent copy to your home screen.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
        when {
            startup.phase == StartupPhase.UNKNOWN || busy -> com.cagri.real.ui.components.RealSpinner(color = Color.White)
            startup.uid == null -> Button(onClick = onSignIn) { Text("Sign in to REAL") }
            else -> Button(onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (!claimed) { CardShareClient().claim(id); claimed = true }
                        SyncLocal.syncForSharing()
                        onDone()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Could not add this card. Please try again." }
                    finally { busy = false }
                }
            }) { Text(if (error == null) "Add to REAL" else "Try again") }
        }
        error?.let { Text(it, color = Color.Gray, modifier = Modifier.padding(top = 16.dp)) }
        TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
    }
}
