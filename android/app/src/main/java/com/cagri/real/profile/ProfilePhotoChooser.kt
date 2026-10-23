package com.cagri.real.profile

import com.cagri.real.i18n.t
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cagri.real.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ProfilePhotoChooser(owner:String?,onHumation:()->Unit,onBack:()->Unit) {
    val context=LocalContext.current
    val profileOwner=remember { owner }
    val scope=rememberCoroutineScope()
    var preview by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var alive by remember { mutableStateOf(true) }
    val latestPreview by rememberUpdatedState(preview)
    DisposableEffect(Unit){onDispose{alive=false;latestPreview?.delete()}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){ uri ->
        if(uri!=null)scope.launch {
            busy=true;error=null
            try {
                // Finish bounded cleanup even if the user closes this screen during decode.
                withContext(NonCancellable) {
                    val next=ProfilePhotoStorage.prepare(context,uri)
                    if(alive){preview?.delete();preview=next}else next.delete()
                }
            }catch(c:CancellationException){throw c}
            catch(e:Exception){error=e.message?:t("Fotoğraf açılamadı.")}
            finally{busy=false}
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            com.cagri.real.ui.components.RealBackButton(onBack, enabled = !busy)
            Spacer(Modifier.width(12.dp))
            Text(t("Profil görünümün"),color=Color.White,fontSize=23.sp,fontWeight=FontWeight.SemiBold)
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(216.dp).liquidChrome(corners=28.dp).padding(8.dp),contentAlignment=Alignment.Center){
            Crossfade(targetState=preview,animationSpec=tween(180),label="profilePhotoPreview") { photo ->
                if(photo==null)ProfileAvatar(Modifier.fillMaxSize())
                else AsyncImage(photo,contentDescription=t("Kare profil fotoğrafı önizlemesi"),
                    modifier=Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),contentScale=ContentScale.Crop)
            }
        }
        Text(t("Fotoğrafın kare olarak ortadan kırpılır. En fazla 512 × 512 piksel; küçük görseller büyütülmez."),
            color=Color(0xFFA4A4AD),fontSize=14.sp,modifier=Modifier.padding(horizontal=8.dp))
        if(error!=null)Text(error!!,color=Color(0xFFFF9F9F),fontSize=13.sp)
        PhotoAction(ActionGlyph.Photo,if(busy)t("Hazırlanıyor…") else if(preview==null)t("Kendi fotoğrafını seç") else t("Başka fotoğraf seç"),!busy){
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if(preview!=null)PhotoAction(ActionGlyph.Check,t("Fotoğrafı kaydet"),!busy){
            val photo=preview ?: return@PhotoAction
            scope.launch {
                busy=true;error=null
                try {
                    withContext(NonCancellable){ProfilePhotoStorage.save(context,photo,profileOwner)}
                    if(alive)onBack()
                }catch(c:CancellationException){throw c}
                catch(e:Exception){error=e.message?:t("Fotoğraf kaydedilemedi.")}
                finally{busy=false}
            }
        }
        PhotoAction(ActionGlyph.Person,t("Humation avatarını düzenle"),!busy,onHumation)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PhotoAction(glyph:ActionGlyph,label:String,enabled:Boolean,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().liquidChrome(corners=22.dp).clickable(enabled=enabled,onClick=onClick).padding(horizontal=18.dp,vertical=16.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ActionIcon(glyph,Modifier.size(22.dp),color=Color.White.copy(alpha=if(enabled)1f else 0.4f))
        Text(label,color=Color.White.copy(alpha=if(enabled)1f else 0.4f),fontSize=16.sp,fontWeight=FontWeight.Medium)
    }
}
