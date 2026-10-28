package com.cagri.real.ui.components

import com.cagri.real.i18n.t
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.cagri.real.BuildConfig
import com.cagri.real.data.ReminderLocation
import com.google.android.gms.maps.StreetViewPanoramaOptions
import com.google.android.gms.maps.StreetViewPanoramaView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * PlaceDetails.swift on Android: under a card's place, Apple's street-level Look Around (when Apple has it) and the
 * business's phone and website when Apple Maps knows the place. Apple's web map (MapKit JS) does both inside a small
 * WebView with a short token from the REAL Worker; nothing is stored, nothing shows when it is not available.
 * Where Apple has no Look Around, a "Sokak görünümü" chip offers Google's 360° Street View instead: only on tap, and
 * only after the Worker counts the opening against its monthly limits (Google bills each panorama).
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
    var noLookAround by remember(latitude, longitude) { mutableStateOf(false) }
    var streetView by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var streetViewOpen by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var streetViewEmbed by remember(latitude, longitude) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(latitude, longitude) { token = mapkitToken() }
    // Apple answers with an error where it has no imagery, or sometimes never answers at all.
    LaunchedEffect(latitude, longitude, token) { if (token != null) { delay(8_000); if (!lookAround) noLookAround = true } }
    LaunchedEffect(noLookAround) {
        if (noLookAround && !lookAround && BuildConfig.STREETVIEW_ENABLED) streetView = streetViewRequest(latitude, longitude, check = true)
    }
    val bridge = remember(latitude, longitude) {
        object {
            @JavascriptInterface fun lookAround(ok: Boolean) { lookAround = ok; if (!ok) noLookAround = true }
            @JavascriptInterface fun place(json: String) {
                val o = runCatching { JSONObject(json) }.getOrNull() ?: return
                phone = o.optString("phone").filter { it.isDigit() || it == '+' }.takeIf { it.length >= 5 }
                site = o.optString("url").takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
        }
    }
    // One WebView for both the preview and full screen: tapping the preview moves this same live scene into the
    // full-screen window (as iOS grows its preview), instead of loading a second Look Around there from scratch.
    val webView = remember(latitude, longitude, token) {
        val t = token ?: return@remember null
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addJavascriptInterface(bridge, "RealPlace")
            loadDataWithBaseURL(MAPKIT_ORIGIN, lookAroundPage(t, latitude, longitude, place.placeName), "text/html", "utf-8", null)
        }
    } ?: return
    DisposableEffect(webView) { onDispose { webView.destroy() } }

    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Hidden (0 dp) while Apple loads, but the WebView itself is always laid out at the preview's full size: a
        // WebView first laid out 1 dp tall started Look Around in a 1 px viewport and stuck half-drawn.
        Box(
            Modifier.fillMaxWidth().height(if (lookAround) PREVIEW_HEIGHT else 0.dp).clip(RoundedCornerShape(16.dp))
        ) {
            if (!full) AndroidView(
                factory = { webView.detached() },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(PREVIEW_HEIGHT)
            )
            if (lookAround) Box(Modifier.fillMaxSize().clickable(role = Role.Button) { full = true })
        }
        if (full) LookAroundFullScreen(webView) { full = false }
        streetViewOpen?.let { pano -> StreetViewFullScreen(pano) { streetViewOpen = null } }
        if (streetViewEmbed) StreetViewEmbedFullScreen(latitude, longitude) { streetViewEmbed = false }
        val streetViewChip = (if (BuildConfig.STREETVIEW_ENABLED) streetView != null else noLookAround) && !lookAround
        if (phone != null || site != null || streetViewChip) {
            // Scrolls sideways instead of squeezing the last chip into a column (large font sizes).
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (streetViewChip) PlaceChip(ActionGlyph.Binoculars, t("Sokak görünümü")) {
                    if (BuildConfig.STREETVIEW_ENABLED) {
                        scope.launch {
                            // The Worker counts this opening; a full limit (or no panorama any more) takes the chip away.
                            val pano = streetViewRequest(latitude, longitude, check = false)
                            if (pano != null) streetViewOpen = pano else streetView = null
                        }
                    } else {
                        streetViewEmbed = true
                    }
                }
                phone?.let { tel ->
                    PlaceChip(ActionGlyph.Phone, t("Ara")) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))) } }
                }
                site?.let { url ->
                    PlaceChip(ActionGlyph.Compass, t("Web sitesi")) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                }
            }
        }
    }
}

/** Look Around filling the screen, moved by hand; the round ✕ (or back) closes it and gives the view back to the card. */
@Composable
private fun LookAroundFullScreen(webView: WebView, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { webView.detached() }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 16.sp) }
        }
    }
}

/** Google's own embeddable Street View (the Share → Embed map iframe), keyless and free; may show a user photo sphere; the paid SDK path replaces it when STREETVIEW_ENABLED. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun StreetViewEmbedFullScreen(lat: Double, lng: Double, onClose: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(lat, lng) {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lngStr = String.format(Locale.US, "%.6f", lng)
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{position:fixed;inset:0;border:0;width:100%;height:100%}</style></head>
            <body><iframe allowfullscreen referrerpolicy="no-referrer-when-downgrade"
            src="https://www.google.com/maps/embed?pb=!6m6!1m5!2m2!1d$latStr!2d$lngStr!4f-0!5f1"></iframe></body></html>
        """.trimIndent()
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            setBackgroundColor(android.graphics.Color.BLACK)
            // Google's embed lives in a third-party iframe: without its cookies and a chrome client it stayed black.
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = android.webkit.WebChromeClient()
            loadDataWithBaseURL(MAPKIT_ORIGIN, html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { webView.detached() }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 16.sp) }
        }
    }
}

/**
 * Google's 360° Street View filling the screen, moved by hand; the ✕ (or back) closes it. The panorama is created
 * here and only here, after the Worker allowed it: creating one is what Google bills.
 */
@Composable
private fun StreetViewFullScreen(panoId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = remember(panoId) {
        StreetViewPanoramaView(context, StreetViewPanoramaOptions().panoramaId(panoId).streetNamesEnabled(true)).apply {
            onCreate(null)
            onResume()
        }
    }
    DisposableEffect(view) { onDispose { view.onPause(); view.onDestroy() } }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { (view.parent as? ViewGroup)?.removeView(view); view }, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(40.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontSize = 16.sp) }
        }
    }
}

/** The shared WebView, taken out of the place it was shown in before (the card's preview or the full screen). */
private fun WebView.detached(): WebView = apply { (parent as? ViewGroup)?.removeView(this) }

private val PREVIEW_HEIGHT = 140.dp

@Composable
private fun PlaceChip(glyph: ActionGlyph, label: String, onClick: () -> Unit) {
    val tint = Color.White.copy(alpha = 0.9f)
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(glyph, Modifier.size(14.dp), tint, filled = glyph != ActionGlyph.Compass)
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

private const val MAPKIT_ORIGIN = "https://real-backend-staging.cagrigul1907.workers.dev"
private const val PLACE_SEARCH = "$MAPKIT_ORIGIN/api/place-search"
private const val STREET_VIEW = "$MAPKIT_ORIGIN/api/street-view"

/**
 * Google's panorama id at the pin, or null. [check] only asks whether there is one and room in the monthly limits
 * (free, nothing counted); without it the Worker counts one opening and answers 429 when a limit is full.
 */
private suspend fun streetViewRequest(lat: Double, lng: Double, check: Boolean): String? = withContext(Dispatchers.IO) {
    runCatching {
        val response = com.cagri.real.network.WorkerClient(com.cagri.real.platform.android.auth.AndroidBackend.workerTransport)
            .postJson(STREET_VIEW, """{"lat":$lat,"lng":$lng,"check":$check}""", 10_000, 15_000, readErrorBody = false)
        if (response.statusCode !in 200..299) return@runCatching null
        val json = JSONObject(response.body)
        json.optString("panoId").takeIf { it.isNotBlank() && (!check || json.optBoolean("available")) }
    }.getOrNull()
}

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
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}#la{position:fixed;inset:0}</style>
<script src="https://cdn.apple-mapkit.com/mk/x/mapkit.core.js" crossorigin async data-callback="initMapKit"
 data-token="$token" data-libraries="look-around,services" data-language="${if (com.cagri.real.i18n.I18n.isEnglish) "en" else "tr"}"></script></head>
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
