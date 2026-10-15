package com.cagri.real

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cagri.real.ui.components.ActionGlyph
import com.cagri.real.ui.components.ActionIcon
import com.cagri.real.ui.theme.RealTheme

class AvatarEditorActivity : ComponentActivity() {
    private var profileOwner: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileOwner = com.cagri.real.profile.ProfileAccountStore.currentOwner()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        setContent {
            RealTheme {
                AvatarEditorScreen(onBack = { finish() })
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun AvatarEditorScreen(onBack: () -> Unit) {
        var humation by rememberSaveable { mutableStateOf(false) }
        if(!humation) {
            com.cagri.real.profile.ProfilePhotoChooser(owner=profileOwner,onHumation={humation=true},onBack=onBack)
            return
        }
        val context = LocalContext.current
        val prefs = remember(context, profileOwner) { com.cagri.real.profile.ProfileAccountStore.preferences(context, profileOwner) }
        val savedState = prefs.getString(KEY_STATE_JSON, "") ?: ""
        val savedStateBase64 = Base64.encodeToString(savedState.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.cagri.real.ui.components.RealBackButton({ humation=false })
                Text(
                    text = "Avatar",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {}
                            addJavascriptInterface(AvatarBridge(), "Android")
                            loadDataWithBaseURL(
                                "https://humation.app/",
                                buildAvatarHtml(savedStateBase64),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private inner class AvatarBridge {
        @JavascriptInterface
        fun saveAvatar(stateJson: String, pngDataUrl: String) {
            try {
                require(pngDataUrl.length <= 3 * 1024 * 1024 && stateJson.length <= 64 * 1024) { "Avatar verisi çok büyük" }
                val base64 = pngDataUrl.substringAfter("base64,", missingDelimiterValue = "")
                require(base64.isNotBlank()) { "PNG payload is empty" }
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                require(bytes.size <= 2 * 1024 * 1024) { "Avatar boyutu desteklenmiyor" }
                require(bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() }) { "PNG gerekli" }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512) { "Avatar boyutu desteklenmiyor" }
                com.cagri.real.profile.ProfileAccountStore.save(this@AvatarEditorActivity,
                    profileOwner, "avatar", "png", stateJson) { file -> file.writeBytes(bytes) }
                runOnUiThread {
                    com.cagri.real.ui.components.RealToast.show(this@AvatarEditorActivity, "Avatar kaydedildi")
                    finish()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    com.cagri.real.ui.components.RealToast.show(this@AvatarEditorActivity, "Avatar kaydedilemedi")
                }
            }
        }
    }

    private fun buildAvatarHtml(savedStateBase64: String): String = """
<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover" />
<style>
:root { color-scheme: dark; }
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { overscroll-behavior-y: contain; }
body { margin:0; background:#000; color:#f7f7f8; font-family:Inter,system-ui,-apple-system,sans-serif; }
.wrap { padding:4px 16px 40px; max-width:760px; margin:0 auto; }
.hero { display:flex; justify-content:center; padding:4px 0 10px; position:relative; z-index:41; }
.previewShell {
  width:clamp(268px,74vw,316px);
  aspect-ratio:1/1;
  border-radius:52px;
  overflow:hidden;
  border:1px solid rgba(255,255,255,.15);
  background:#14151c;
  box-shadow:0 24px 64px rgba(0,0,0,.42);
}
#preview, #preview svg { width:100%; height:100%; display:block; filter:none!important; -webkit-filter:none!important; }
.actions { display:flex; gap:10px; margin:4px 0 14px; }
button { font:inherit; color:inherit; }
.action { flex:1; min-height:46px; border-radius:18px; border:1px solid rgba(255,255,255,.14); background:#15151a; font-weight:650; }
.action.primary { background:#f4f4f5; color:#0d0d10; border-color:#f4f4f5; }
.tabs { display:flex; gap:7px; overflow-x:auto; padding:2px 0 10px; scrollbar-width:none; }
.tabs::-webkit-scrollbar { display:none; }
.tab { white-space:nowrap; padding:10px 14px; border-radius:16px; border:1px solid rgba(255,255,255,.12); background:#111116; color:#a9a9b2; }
.tab.active { color:#fff; background:#24242b; border-color:rgba(255,255,255,.24); }
.grid {
  display:grid;
  grid-template-columns:repeat(4,minmax(0,1fr));
  grid-auto-rows:82px;
  column-gap:10px;
  row-gap:12px;
  height:282px;
  overflow-y:auto;
  overscroll-behavior:contain;
  align-content:start;
  padding:4px 2px 10px;
  scrollbar-width:thin;
  scrollbar-color:rgba(255,255,255,.18) transparent;
}
.grid::-webkit-scrollbar { width:4px; }
.grid::-webkit-scrollbar-thumb { background:rgba(255,255,255,.18); border-radius:999px; }
.part {
  width:100%;
  height:82px;
  min-width:0;
  min-height:82px;
  margin:0;
  padding:5px;
  overflow:hidden;
  border-radius:18px;
  border:1px solid rgba(255,255,255,.26);
  background:#ffffff;
  box-shadow:0 3px 8px rgba(0,0,0,.13);
}
.part img { width:100%; height:100%; max-width:100%; max-height:100%; object-fit:contain; display:block; }
.part.selected {
  border-color:#9fc4ff;
  background:#ffffff;
  box-shadow:0 0 0 2px rgba(130,180,255,.42), 0 4px 11px rgba(0,0,0,.16);
}
.sectionTitle { font-size:15px; font-weight:700; margin:18px 0 10px; color:#efeff2; }
.colors { display:grid; grid-template-columns:repeat(5,1fr); gap:9px; }
.color { display:flex; flex-direction:column; align-items:center; gap:7px; color:#9d9da7; font-size:11px; }
.colorSwatch {
  width:50px;
  height:50px;
  border-radius:17px;
  border:1px solid rgba(255,255,255,.20);
  padding:0;
  box-shadow:inset 0 1px 0 rgba(255,255,255,.30), 0 8px 20px rgba(0,0,0,.22);
}
.note { color:#777781; font-size:11px; line-height:16px; text-align:center; margin-top:18px; }
.loading { color:#8d8d96; text-align:center; padding:34px 8px; }
.error { color:#ff8e8e; text-align:center; padding:24px 8px; line-height:20px; }

/* REAL Liquid color picker */
.colorScrim {
  position:fixed;
  inset:0;
  z-index:40;
  opacity:0;
  pointer-events:none;
  background:rgba(0,0,0,.40);
  backdrop-filter:blur(5px);
  -webkit-backdrop-filter:blur(5px);
  transition:opacity .18s ease;
}
.colorScrim.open { opacity:1; pointer-events:auto; }
.colorSheet {
  position:fixed;
  z-index:42;
  left:12px;
  right:12px;
  bottom:12px;
  max-width:520px;
  margin:0 auto;
  padding:10px 16px 16px;
  border-radius:30px;
  border:1px solid rgba(255,255,255,.19);
  background:linear-gradient(180deg,rgba(43,43,52,.82),rgba(20,20,27,.88));
  box-shadow:inset 0 1px 0 rgba(255,255,255,.16),0 28px 80px rgba(0,0,0,.55);
  backdrop-filter:blur(28px) saturate(1.35);
  -webkit-backdrop-filter:blur(28px) saturate(1.35);
  transform:translateY(calc(100% + 28px)) scale(.985);
  opacity:0;
  pointer-events:none;
  transition:transform .24s cubic-bezier(.16,1,.3,1),opacity .18s ease;
}
.colorSheet.open { transform:translateY(0) scale(1); opacity:1; pointer-events:auto; }
.sheetHandle { width:38px; height:4px; border-radius:999px; background:rgba(255,255,255,.24); margin:1px auto 9px; }
.sheetHeader { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-bottom:12px; }
.sheetTitle { font-size:18px; font-weight:650; letter-spacing:-.2px; }
.sheetClose { width:34px; height:34px; border:0; border-radius:50%; background:rgba(255,255,255,.10); color:#fff; font-size:22px; line-height:1; }
.svPad {
  position:relative;
  width:100%;
  height:174px;
  border-radius:22px;
  overflow:hidden;
  touch-action:none;
  border:1px solid rgba(255,255,255,.13);
  box-shadow:inset 0 1px 0 rgba(255,255,255,.10);
}
.svPad::before { content:""; position:absolute; inset:0; background:linear-gradient(to right,#fff,rgba(255,255,255,0)); }
.svPad::after { content:""; position:absolute; inset:0; background:linear-gradient(to top,#000,rgba(0,0,0,0)); }
.svCursor { position:absolute; z-index:2; width:22px; height:22px; margin:-11px 0 0 -11px; border-radius:50%; border:2px solid #fff; box-shadow:0 1px 5px rgba(0,0,0,.65); pointer-events:none; }
.hueSlider { width:100%; height:28px; margin:10px 0 7px; appearance:none; -webkit-appearance:none; background:transparent; }
.hueSlider::-webkit-slider-runnable-track { height:14px; border-radius:999px; background:linear-gradient(to right,#f00,#ff0,#0f0,#0ff,#00f,#f0f,#f00); border:1px solid rgba(255,255,255,.15); }
.hueSlider::-webkit-slider-thumb { -webkit-appearance:none; width:24px; height:24px; margin-top:-6px; border-radius:50%; background:#fff; border:2px solid rgba(0,0,0,.26); box-shadow:0 2px 7px rgba(0,0,0,.45); }
.hexRow { display:flex; gap:9px; align-items:center; margin-top:2px; }
.colorPreviewDot { width:42px; height:42px; flex:0 0 42px; border-radius:14px; border:1px solid rgba(255,255,255,.20); }
.hexField { flex:1; height:42px; border:1px solid rgba(255,255,255,.13); border-radius:15px; background:rgba(255,255,255,.075); color:#fff; padding:0 13px; font:600 15px Inter,system-ui,sans-serif; outline:none; }
.sheetActions { display:flex; gap:9px; margin-top:12px; }
.sheetButton { flex:1; min-height:44px; border-radius:16px; border:1px solid rgba(255,255,255,.13); background:rgba(255,255,255,.08); font-weight:650; }
.sheetButton.primary { background:#f4f4f5; color:#0d0d10; border-color:#f4f4f5; }
</style>
</head>
<body>
<div class="wrap">
  <div class="hero"><div class="previewShell"><div id="preview"><div class="loading">Avatar yükleniyor…</div></div></div></div>
  <div class="actions">
    <button class="action" id="randomize">Rastgele</button>
    <button class="action primary" id="save">Kaydet</button>
  </div>
  <div class="tabs" id="tabs"></div>
  <div class="grid" id="parts"></div>
  <div class="sectionTitle">Renkler</div>
  <div class="colors" id="colors"></div>
  <div class="note">Humation 1 avatar parçaları · MIT lisansı</div>
</div>
<div class="colorScrim" id="colorScrim"></div>
<div class="colorSheet" id="colorSheet" role="dialog" aria-modal="true">
  <div class="sheetHandle"></div>
  <div class="sheetHeader">
    <div class="sheetTitle" id="colorSheetTitle">Renk</div>
    <button class="sheetClose" id="colorClose" aria-label="Kapat">×</button>
  </div>
  <div class="svPad" id="svPad"><div class="svCursor" id="svCursor"></div></div>
  <input class="hueSlider" id="hueSlider" type="range" min="0" max="360" step="1" />
  <div class="hexRow">
    <div class="colorPreviewDot" id="colorPreviewDot"></div>
    <input class="hexField" id="hexField" type="text" maxlength="7" spellcheck="false" inputmode="text" />
  </div>
  <div class="sheetActions">
    <button class="sheetButton" id="colorCancel">Vazgeç</button>
    <button class="sheetButton primary" id="colorApply">Uygula</button>
  </div>
</div>
<script type="module">
try {
  const core = await import('https://esm.sh/@humation/core@1.0.3');
  const assets = await import('https://esm.sh/@humation/assets-humation-1@1.0.3');
  const { createAvatar, createPartPreview, getPartsForSlot } = core;
  const { humation1 } = assets;

  const rawSaved = atob('$savedStateBase64');
  let saved = null;
  if (rawSaved) { try { saved = JSON.parse(rawSaved); } catch (_) {} }

  const state = saved || {
    selections: { ...humation1.defaults.selections },
    colors: { ...humation1.defaults.colors },
    background: humation1.defaults.background || 'F6F5F4'
  };
  state.selections = { ...humation1.defaults.selections, ...(state.selections || {}) };
  state.colors = { ...humation1.defaults.colors, ...(state.colors || {}) };
  state.background = state.background || humation1.defaults.background || 'F6F5F4';

  const slots = [
    ['head','Head'], ['body','Body'], ['item','Item'], ['glasses','Glasses']
  ];
  const colorSlots = [
    ['stroke','Çizgi'], ['hair','Saç'], ['skin','Ten'], ['clothes','Kıyafet'], ['background','Arka plan']
  ];
  let activeSlot = 'head';

  const tabsEl = document.getElementById('tabs');
  const partsEl = document.getElementById('parts');
  const colorsEl = document.getElementById('colors');
  const previewEl = document.getElementById('preview');

  function hex(value, fallback) {
    const v = String(value || fallback || '000000').replace('#','');
    return '#' + (v.length === 6 ? v : fallback);
  }

  function avatarOptions() {
    return {
      selections: state.selections,
      colors: state.colors,
      background: state.background,
      crop: 'avatar'
    };
  }

  function renderPreview() {
    const svg = createAvatar(humation1, avatarOptions()).toString();
    previewEl.innerHTML = svg;
    const node = previewEl.querySelector('svg');
    if (node) { node.style.width='100%'; node.style.height='100%'; node.style.display='block'; }
  }

  function renderTabs() {
    tabsEl.innerHTML = '';
    for (const [id,label] of slots) {
      const b = document.createElement('button');
      b.className = 'tab' + (id === activeSlot ? ' active' : '');
      b.textContent = label;
      b.onclick = () => { activeSlot = id; renderTabs(); renderParts(); };
      tabsEl.appendChild(b);
    }
  }

  function renderParts() {
    partsEl.innerHTML = '';
    const parts = getPartsForSlot(humation1, activeSlot);
    for (const part of parts) {
      const b = document.createElement('button');
      const chosen = state.selections[activeSlot];
      const isSelected = chosen === part.name || chosen === part.id;
      b.className = 'part' + (isSelected ? ' selected' : '');
      b.title = part.name;
      try {
        const img = document.createElement('img');
        img.alt = part.name;
        img.src = createPartPreview(humation1, part, { colors: state.colors }).toDataUri();
        b.appendChild(img);
      } catch (_) {
        b.textContent = part.name;
      }
      b.onclick = () => {
        state.selections[activeSlot] = part.name;
        renderPreview();
        renderParts();
      };
      partsEl.appendChild(b);
    }
  }

  let pickerId = null;
  let pickerLabel = '';
  let pickerOriginal = null;
  let pickerH = 0, pickerS = 0, pickerV = 100;
  const colorScrim = document.getElementById('colorScrim');
  const colorSheet = document.getElementById('colorSheet');
  const colorSheetTitle = document.getElementById('colorSheetTitle');
  const svPad = document.getElementById('svPad');
  const svCursor = document.getElementById('svCursor');
  const hueSlider = document.getElementById('hueSlider');
  const hexField = document.getElementById('hexField');
  const colorPreviewDot = document.getElementById('colorPreviewDot');

  function hsvToHex(h,s,v) {
    s/=100; v/=100;
    const c=v*s, x=c*(1-Math.abs((h/60)%2-1)), m=v-c;
    let r=0,g=0,b=0;
    if(h<60){r=c;g=x;} else if(h<120){r=x;g=c;} else if(h<180){g=c;b=x;}
    else if(h<240){g=x;b=c;} else if(h<300){r=x;b=c;} else {r=c;b=x;}
    const q=n=>Math.round((n+m)*255).toString(16).padStart(2,'0').toUpperCase();
    return q(r)+q(g)+q(b);
  }

  function hexToHsv(value) {
    const clean=String(value||'000000').replace('#','').padEnd(6,'0').slice(0,6);
    const r=parseInt(clean.slice(0,2),16)/255, g=parseInt(clean.slice(2,4),16)/255, b=parseInt(clean.slice(4,6),16)/255;
    const max=Math.max(r,g,b), min=Math.min(r,g,b), d=max-min;
    let h=0;
    if(d!==0){ if(max===r) h=60*(((g-b)/d)%6); else if(max===g) h=60*((b-r)/d+2); else h=60*((r-g)/d+4); }
    if(h<0)h+=360;
    return [h, max===0?0:(d/max)*100, max*100];
  }

  function activeColorHex() {
    if (pickerId === 'background') return String(state.background || 'F6F5F4').replace('#','').toUpperCase();
    return String(state.colors[pickerId] || humation1.defaults.colors[pickerId] || '000000').replace('#','').toUpperCase();
  }

  function setActiveColor(hexValue, rerender=true) {
    const clean=String(hexValue||'').replace('#','').toUpperCase();
    if(!/^[0-9A-F]{6}$/.test(clean)) return false;
    if (pickerId === 'background') state.background = clean;
    else state.colors[pickerId] = clean;
    if(rerender){ renderPreview(); renderColors(); }
    return true;
  }

  function refreshPicker() {
    const value=hsvToHex(pickerH,pickerS,pickerV);
    svPad.style.backgroundColor='hsl(' + pickerH + ',100%,50%)';
    svCursor.style.left=pickerS + '%';
    svCursor.style.top=(100-pickerV) + '%';
    hueSlider.value=String(Math.round(pickerH));
    hexField.value='#'+value;
    colorPreviewDot.style.background='#'+value;
    setActiveColor(value,true);
  }

  function openColorPicker(id,label) {
    pickerId=id; pickerLabel=label; pickerOriginal=activeColorHex();
    [pickerH,pickerS,pickerV]=hexToHsv(pickerOriginal);
    colorSheetTitle.textContent=label;
    colorScrim.classList.add('open');
    colorSheet.classList.add('open');
    refreshPicker();
  }

  function closeColorPicker(commit) {
    if(!commit && pickerId && pickerOriginal) setActiveColor(pickerOriginal,true);
    colorScrim.classList.remove('open');
    colorSheet.classList.remove('open');
    pickerId=null;
  }

  function updateSvFromPointer(ev) {
    const rect=svPad.getBoundingClientRect();
    const point=ev.touches ? ev.touches[0] : ev;
    const x=Math.max(0,Math.min(rect.width,point.clientX-rect.left));
    const y=Math.max(0,Math.min(rect.height,point.clientY-rect.top));
    pickerS=x/rect.width*100;
    pickerV=(1-y/rect.height)*100;
    refreshPicker();
  }

  let svDragging=false;
  svPad.addEventListener('pointerdown', e=>{svDragging=true;svPad.setPointerCapture?.(e.pointerId);updateSvFromPointer(e);});
  svPad.addEventListener('pointermove', e=>{if(svDragging)updateSvFromPointer(e);});
  svPad.addEventListener('pointerup', ()=>{svDragging=false;});
  svPad.addEventListener('pointercancel', ()=>{svDragging=false;});
  hueSlider.addEventListener('input', ()=>{pickerH=Number(hueSlider.value);refreshPicker();});
  hexField.addEventListener('change', ()=>{
    const value=hexField.value.replace('#','').toUpperCase();
    if(/^[0-9A-F]{6}$/.test(value)) { [pickerH,pickerS,pickerV]=hexToHsv(value); refreshPicker(); }
    else hexField.value='#'+activeColorHex();
  });
  document.getElementById('colorClose').onclick=()=>closeColorPicker(false);
  document.getElementById('colorCancel').onclick=()=>closeColorPicker(false);
  document.getElementById('colorApply').onclick=()=>closeColorPicker(true);
  colorScrim.onclick=()=>closeColorPicker(false);
  document.querySelector('.hero').onclick=()=>{ if (colorSheet.classList.contains('open')) closeColorPicker(false); };

  function renderColors() {
    colorsEl.innerHTML = '';
    for (const [id,label] of colorSlots) {
      const wrap = document.createElement('div');
      wrap.className = 'color';
      const button = document.createElement('button');
      button.type='button';
      button.className='colorSwatch';
      const value = id === 'background' ? hex(state.background,'F6F5F4') : hex(state.colors[id], humation1.defaults.colors[id] || '000000');
      button.style.background=value;
      button.setAttribute('aria-label', label + ' rengini düzenle');
      button.onclick=()=>openColorPicker(id,label);
      const span = document.createElement('span');
      span.textContent = label;
      wrap.append(button, span);
      colorsEl.appendChild(wrap);
    }
  }

  function randomPart(slot) {
    const list = getPartsForSlot(humation1, slot);
    if (!list.length) return;
    const p = list[Math.floor(Math.random() * list.length)];
    state.selections[slot] = p.name;
  }

  document.getElementById('randomize').onclick = () => {
    for (const [slot] of slots) randomPart(slot);
    renderPreview(); renderParts();
  };

  async function svgToPngDataUrl(svg) {
    return await new Promise((resolve, reject) => {
      const blob = new Blob([svg], {type:'image/svg+xml;charset=utf-8'});
      const url = URL.createObjectURL(blob);
      const image = new Image();
      image.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = 512; canvas.height = 512;
          const ctx = canvas.getContext('2d');
          ctx.clearRect(0,0,512,512);
          ctx.drawImage(image,0,0,512,512);
          URL.revokeObjectURL(url);
          resolve(canvas.toDataURL('image/png'));
        } catch (e) { URL.revokeObjectURL(url); reject(e); }
      };
      image.onerror = (e) => { URL.revokeObjectURL(url); reject(e); };
      image.src = url;
    });
  }

  document.getElementById('save').onclick = async () => {
    const button = document.getElementById('save');
    button.disabled = true; button.textContent = 'Kaydediliyor…';
    try {
      const svg = createAvatar(humation1, avatarOptions()).toString();
      const png = await svgToPngDataUrl(svg);
      Android.saveAvatar(JSON.stringify(state), png);
    } catch (e) {
      button.disabled = false; button.textContent = 'Kaydet';
      alert('Avatar kaydedilemedi.');
    }
  };

  renderTabs(); renderParts(); renderColors(); renderPreview();
} catch (e) {
  document.getElementById('preview').innerHTML = '<div class="error">Humation avatar parçaları yüklenemedi.<br>İnternet bağlantısını kontrol et.</div>';
  document.getElementById('parts').innerHTML = '<div class="error" style="grid-column:1/-1">Avatar editörü ilk kullanımda Humation paketlerini internetten yükler.</div>';
  console.error(e);
}
</script>
</body>
</html>
""".trimIndent()

    companion object {
        const val PROFILE_PREFS = "real_profile"
        const val KEY_STATE_JSON = "avatar_state_json"
        const val KEY_IMAGE_PATH = "avatar_image_path"
        const val KEY_REVISION = "avatar_revision"
    }
}
