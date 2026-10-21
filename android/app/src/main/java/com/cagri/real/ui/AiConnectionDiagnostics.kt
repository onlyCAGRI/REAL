package com.cagri.real.ui

import com.cagri.real.platform.android.network.diagnosticPost
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manual only: opening Settings never sends a network request. */
@Composable
internal fun AiConnectionDiagnostics() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var lastResult by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var workerText by rememberSaveable { mutableStateOf("Henüz test edilmedi") }
    var workerState by rememberSaveable { mutableStateOf("idle") }
    var testedAt by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AiProbeResult>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text("AI bağlantı testi", color = Color.White, fontSize = 15.sp)
        }
        if (expanded) {
            Text(
                "Cloudflare, Gemini modelleri ve Groq yedek sağlayıcısını yalnızca düğmeye basınca kontrol eder. Her satır ayrı bir test isteğidir.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            ProbeStatus("Cloudflare", workerText, workerState)
            results.forEach { ProbeStatus(it.title, it.message, it.state) }
            if (results.isEmpty() && lastResult.isNotEmpty()) {
                Text(lastResult, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            if (testedAt.isNotEmpty()) {
                Text("Son test: $testedAt · Anlık ölçüm", color = Color.Gray, fontSize = 11.sp)
            }
            TextButton(enabled = !running, onClick = {
                running = true
                workerText = "Kontrol ediliyor…"
                workerState = "idle"
                results = emptyList()
                lastResult = ""
                testedAt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                scope.launch {
                    try {
                        val started = SystemClock.elapsedRealtime()
                        val health = diagnosticPost("/api/ai-health", JSONObject())
                        val elapsed = SystemClock.elapsedRealtime() - started
                        if (!health.optBoolean("ok") || health.optInt("diagnosticVersion") < 2) {
                            error("Groq yedekli yeni Worker kodunu yükle.")
                        }
                        workerState = if (elapsed <= 3000) "good" else "slow"
                        workerText = "Ulaşıldı · ${seconds(elapsed)} sn"

                        val initial = mutableListOf<AiProbeResult>()
                        val probes = mutableListOf<AiProbeSpec>()

                        if (!health.optBoolean("geminiConfigured")) {
                            initial += AiProbeResult(
                                key = "gemini-config",
                                title = "Gemini",
                                message = "GEMINI_API_KEY Worker’da ayarlı değil",
                                state = "failed"
                            )
                        } else {
                            val models = health.optJSONArray("models")
                            val names = (0 until (models?.length() ?: 0))
                                .map { models!!.getString(it) }
                                .take(3)
                            names.forEach { model ->
                                val key = "gemini:$model"
                                initial += AiProbeResult(key, "Gemini · $model", "Test ediliyor…", "idle")
                                probes += AiProbeSpec(key, "gemini", model)
                            }
                        }

                        if (!health.optBoolean("groqConfigured")) {
                            initial += AiProbeResult(
                                key = "groq-config",
                                title = "Groq",
                                message = "GROQ_API_KEY Worker’da ayarlı değil",
                                state = "failed"
                            )
                        } else {
                            val model = health.optString("groqModel", "openai/gpt-oss-20b")
                            val key = "groq:$model"
                            initial += AiProbeResult(key, "Groq · $model", "Test ediliyor…", "idle")
                            probes += AiProbeSpec(key, "groq", model)
                        }

                        results = initial
                        supervisorScope {
                            probes.map { probe ->
                                launch {
                                    val result = try {
                                        val payload = JSONObject().put("provider", probe.provider)
                                        if (probe.provider == "gemini") payload.put("model", probe.model)
                                        val response = diagnosticPost("/api/ai-diagnostic", payload)
                                        val ms = response.optLong("durationMs")
                                        val ok = response.optBoolean("ok")
                                        AiProbeResult(
                                            key = probe.key,
                                            title = if (probe.provider == "groq") "Groq · ${probe.model}" else "Gemini · ${probe.model}",
                                            message = if (ok) {
                                                "${if (response.optString("state") == "slow") "Yavaş yanıt" else "Yanıt geldi"} · ${seconds(ms)} sn"
                                            } else {
                                                "${probeError(response.optString("error"), probe.provider)} · ${seconds(ms)} sn"
                                            },
                                            state = if (ok) response.optString("state", "good") else "failed"
                                        )
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        AiProbeResult(
                                            key = probe.key,
                                            title = if (probe.provider == "groq") "Groq · ${probe.model}" else "Gemini · ${probe.model}",
                                            message = error.message ?: "Bağlantı hatası",
                                            state = "failed"
                                        )
                                    }
                                    results = results.map { if (it.key == probe.key) result else it }
                                }
                            }.joinAll()
                        }
                        lastResult = results.joinToString("\n") { "${it.title}: ${it.message}" }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        workerState = "failed"
                        workerText = error.message ?: "Bağlantı kurulamadı"
                    } finally {
                        running = false
                    }
                }
            }) {
                Text(if (running) "Test ediliyor…" else "Bağlantıyı test et")
            }
        }
    }
}

private data class AiProbeSpec(val key: String, val provider: String, val model: String)
private data class AiProbeResult(val key: String, val title: String, val message: String, val state: String)
private fun seconds(ms: Long) = String.format(Locale.getDefault(), "%.1f", ms / 1000.0)

@Composable
private fun ProbeStatus(title: String, message: String, state: String) {
    val color = when (state) {
        "good" -> Color(0xFF86DCA0)
        "slow" -> Color(0xFFFFCF75)
        "failed" -> Color(0xFFFF8A86)
        else -> Color(0xFFADB0B8)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(color.copy(alpha = 0.07f)).padding(8.dp)) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(message, color = color, fontSize = 12.sp)
    }
}

private fun probeError(code: String, provider: String): String = when (code) {
    "QUOTA_LIMIT" -> "Kota / hız sınırı (429)"
    "SERVICE_BUSY" -> "Servis yoğun (503)"
    "TEST_TIMEOUT", "UPSTREAM_TIMEOUT", "TOTAL_TIMEOUT" -> "Yanıt süresi doldu"
    "MODEL_NOT_FOUND" -> "Model bulunamadı (404)"
    "AUTH_ERROR", "ACCESS_DENIED" -> if (provider == "groq") "Groq anahtar / erişim hatası" else "Gemini anahtar / erişim hatası"
    "UNEXPECTED_TEST_RESPONSE" -> "Yanıt geldi ancak OK testi geçmedi"
    "GEMINI_NOT_CONFIGURED" -> "Gemini yapılandırılmamış"
    "GROQ_NOT_CONFIGURED" -> "Groq yapılandırılmamış"
    else -> code.ifBlank { "Geçersiz yanıt" }
}

