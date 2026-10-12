package com.cagri.real.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cagri.real.platform.android.ai.*
import com.cagri.real.platform.android.auth.AndroidBackend
import com.cagri.real.ui.components.PermissionGlyph
import com.cagri.real.ui.components.PermissionSheet
import com.cagri.real.ui.connections.ConnectionPill
import com.cagri.real.ui.theme.RealType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AiReportButton(feature: AiReportFeature,title: String,content: String,modifier: Modifier=Modifier) {
    val uid=AndroidBackend.auth.currentUser()?.id
    var open by remember(uid,feature,title,content) { mutableStateOf(false) }
    TextButton(onClick={open=true},modifier=modifier,enabled=uid!=null) {
        Text("Öneriyi bildir",style=RealType.cardMetaPrimary,color=Color.White.copy(alpha=.58f))
    }
    if(open && uid!=null) key(uid,feature,title,content) {
        val scope=rememberCoroutineScope()
        val client=remember(uid) { AiReportClient(uid) }
        var reason by remember { mutableStateOf<AiReportReason?>(null) }
        val id=remember(reason) { UUID.randomUUID().toString() }
        var busy by remember { mutableStateOf(false) }
        var sent by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        Dialog(onDismissRequest={if(!busy)open=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            PermissionSheet(visible=true,title=if(sent) "Bildirimin alındı" else "Bu öneriyi bildir",
                description=if(sent) "Teşekkürler. Bildirimin inceleme için kaydedildi." else "Yalnız bu önerinin başlığı, gösterilen metni ve bildirim nedeni REAL'e gönderilir.",
                glyph=PermissionGlyph.Brand,primaryLabel=if(sent) "Bitti" else if(busy) "Gönderiliyor…" else "Bildirimi gönder",
                primaryEnabled=sent || (!busy && reason!=null),secondaryLabel=if(sent) "Kapat" else "Vazgeç",
                onLater={if(!busy)open=false},onAllow={
                    if(sent)open=false
                    else if(!busy && reason!=null) { busy=true;error=null;scope.launch {
                        try { client.submit(id,feature,reason!!,title,content);sent=true }
                        catch(cancelled:CancellationException) { throw cancelled }
                        catch(failure:Exception) { error=failure.message ?: "Bildirim gönderilemedi." }
                        finally { busy=false }
                    } }
                },extraContent={
                    if(!sent) Column(Modifier.fillMaxWidth().heightIn(max=210.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Konuşman, duygu seçimlerin ve diğer kartların otomatik eklenmez.",style=RealType.cardMetaPrimary,color=Color.White.copy(alpha=.65f))
                        Text(title.take(300),style=RealType.cardBodyProminent,color=Color.White)
                        if(content.isNotBlank())Text(content.take(4000),style=RealType.cardMetaPrimary,color=Color.White.copy(alpha=.65f))
                        AiReportReason.entries.forEach { option ->
                            ConnectionPill(option.label(),{reason=option},enabled=!busy,filled=reason==option,small=true)
                        }
                        error?.let { Text(it,style=RealType.cardMetaPrimary,color=Color(0xFFFFC8B8)) }
                    }
                })
        }
    }
}
private fun AiReportReason.label()=when(this) {
    AiReportReason.HARMFUL -> "Tehlikeli veya zararlı"
    AiReportReason.HARASSMENT -> "Nefret veya taciz"
    AiReportReason.SEXUAL -> "Uygunsuz cinsel içerik"
    AiReportReason.INACCURATE -> "Yanlış veya yanıltıcı"
    AiReportReason.OTHER -> "Diğer uygunsuz içerik"
}
