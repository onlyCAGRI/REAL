package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cagri.real.data.ReminderLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * PlaceDetails.swift on Android: under a card's place, Apple's street-level Look Around (when Apple has it) and the
 * business's phone and website when Apple Maps knows the place. Apple's web map (MapKit JS) does both inside a small
 * WebView with a short token from the REAL Worker; nothing is stored, nothing shows when it is not available.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlaceDetails(place: ReminderLocation, modifier: Modifier = Modifier) {
    val latitude = place.latitude ?: return
    val longitude = place.longitude ?: return
    val context = LocalContext.current
    var token by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var lookAround by remember(latitude, longitude) { mutableStateOf(false) }
    var phone by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var site by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var full by remember(latitude, longitude) { mutableStateOf(false) }
    LaunchedEffect(latitude, longitude) { token = mapkitToken() }
    val html = token?.let { lookAroundPage(it, latitude, longitude, place.placeName) } ?: return

    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val bridge = remember(latitude, longitude) {
            object {
                @JavascriptInterface fun lookAround(ok: Boolean) { lookAround = ok }
                @JavascriptInterface fun place(json: String) {
                    val o = runCatching { JSONObject(json) }.getOrNull() ?: return
                    phone = o.optString("phone").filter { it.isDigit() || it == '+' }.takeIf { it.length >= 5 }
                    site = o.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
                }
            }
        }
        // Kept 1 dp tall while Apple loads; grows to the preview only when a scene came. Tapping it opens Look Around
        // full screen (as on iOS); the small one is only a preview.
        Box(Modifier.fillMaxWidth().height(if (lookAround) 140.dp else 1.dp).clip(RoundedCornerShape(16.dp))) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    addJavascriptInterface(bridge, "RealPlace")
                    loadDataWithBaseURL(MAPKIT_ORIGIN, html, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize()
        )
            if (lookAround) Box(Modifier.fillMaxSize().clickable(role = Role.Button) { full = true })
        }
        if (full) LookAroundFullScreen(token!!, latitude, longitude) { full = false }
        if (phone != null || site != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                phone?.let { tel ->
                    PlaceChip("Ara") { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))) } }
                }
                site?.let { url ->
                    PlaceChip("Web sitesi") { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                }
            }
        }
    }
}

/** Look Around filling the screen, moved by hand; the round ✕ (or back) closes it. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LookAroundFullScreen(token: String, lat: Double, lng: Double, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        loadDataWithBaseURL(MAPKIT_ORIGIN, fullLookAroundPage(token, lat, lng), "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 16.sp) }
        }
    }
}

private fun fullLookAroundPage(token: String, lat: Double, lng: Double): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:#000;overflow:hidden}#la{width:100vw;height:100vh}</style>
<script src="https://cdn.apple-mapkit.com/mk/x/mapkit.core.js" crossorigin async data-callback="initMapKit"
 data-token="$token" data-libraries="look-around" data-language="tr"></script></head>
<body><div id="la"></div><script>
window.initMapKit=()=>{new mapkit.LookAround(document.getElementById('la'),new mapkit.Coordinate($lat,$lng),
 {showsDialogControl:false,showsCloseControl:false});};
</script></body></html>
""".trimIndent()

@Composable
private fun PlaceChip(label: String, onClick: () -> Unit) {
    Text(
        label, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

private const val MAPKIT_ORIGIN = "https://real-backend-staging.cagrigul1907.workers.dev"
private const val PLACE_SEARCH = "$MAPKIT_ORIGIN/api/place-search"

/** A 30-minute MapKit JS token for REAL's page origin; null when offline or signed out. */
private suspend fun mapkitToken(): String? = withContext(Dispatchers.IO) {
    runCatching {
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson(PLACE_SEARCH, """{"mapkit":true}""", 10_000, 15_000, readErrorBody = false)
        if (response.statusCode !in 200..299) return@runCatching null
        JSONObject(response.body).optString("token").takeIf { it.count { c -> c == '.' } == 2 }
    }.getOrNull()
}

/**
 * Look Around at the pin, and the business there: within 150 m and with the card's own place name (a street or an
 * address picked by hand never borrows a nearby shop's phone).
 */
private fun lookAroundPage(token: String, lat: Double, lng: Double, name: String): String = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;background:transparent;overflow:hidden}#la{width:100vw;height:140px}</style>
<script src="https://cdn.apple-mapkit.com/mk/x/mapkit.core.js" crossorigin async data-callback="initMapKit"
 data-token="$token" data-libraries="look-around,services" data-language="tr"></script></head>
<body><div id="la"></div><script>
const LAT=$lat,LNG=$lng,NAME=${JSONObject.quote(name)};
window.initMapKit=()=>{
 const here=new mapkit.Coordinate(LAT,LNG);
 const view=new mapkit.LookAround(document.getElementById('la'),here,{showsDialogControl:false,showsCloseControl:false});
 view.addEventListener('load',()=>RealPlace.lookAround(true));
 view.addEventListener('error',()=>RealPlace.lookAround(false));
 if(!NAME.trim())return;
 const norm=(s)=>(s||'').toLocaleLowerCase('tr').normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/[^a-z0-9]+/g,' ').trim();
 const same=(a,b)=>{a=norm(a);b=norm(b);return a.length>=3&&b.length>=3&&(a===b||a.startsWith(b)||b.startsWith(a))};
 const near=(c)=>{const r=6371000,t=Math.PI/180,a=Math.sin((c.latitude-LAT)*t/2)**2+Math.cos(LAT*t)*Math.cos(c.latitude*t)*Math.sin((c.longitude-LNG)*t/2)**2;return 2*r*Math.asin(Math.sqrt(a))<150};
 new mapkit.Search({region:new mapkit.CoordinateRegion(here,new mapkit.CoordinateSpan(0.006,0.006)),includeAddresses:false,includeQueries:false})
  .search(NAME,(e,d)=>{if(e||!d)return;const p=(d.places||[]).find(x=>x.coordinate&&near(x.coordinate)&&same(x.name,NAME));
   if(p)RealPlace.place(JSON.stringify({phone:p.telephone||'',url:(p.urls&&p.urls[0])||''}));});
};
</script></body></html>
""".trimIndent()
