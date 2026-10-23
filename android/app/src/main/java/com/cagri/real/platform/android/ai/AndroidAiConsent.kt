package com.cagri.real.platform.android.ai

import com.cagri.real.i18n.t
import android.content.Context
import com.cagri.real.ai.*
import com.cagri.real.platform.android.auth.AndroidBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Local, UID-scoped decisions. Never contains speech, cards, email or device identifiers.
 * Device timestamps are not server-attested evidence. Account deletion removes this file.
 */
object AndroidAiConsent {
    private lateinit var directory: File
    private val fileLock = Any()

    data class Decision(
        val allowed: Boolean,
        val disclosureVersion: Int,
        val decidedAtEpochMillis: Long?,
        val scope: String,
        val source: String
    )

    fun initialize(context: Context) { directory = File(context.noBackupFilesDir, "ai_consent") }
    private fun file(uid: String) = AtomicFile(File(directory, MessageDigest.getInstance("SHA-256")
        .digest(uid.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } + ".json"))

    private fun read(uid: String): JSONObject {
        val atomic = file(uid)
        // openRead also recovers an interrupted AtomicFile write (.bak).
        return try {
            atomic.openRead().use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
        } catch (missing: java.io.FileNotFoundException) {
            if (atomic.baseFile.exists()) throw missing
            JSONObject()
        }
    }

    private fun scope(feature: AiFeature) = when (feature) {
        AiFeature.VOICE -> "VOICE_GROQ_TEXT_CONTEXT_V1"
        AiFeature.MOOD -> "MOOD_GROQ_PREFERENCES_HISTORY_V1"
        AiFeature.DISCOVERY -> "DISCOVERY_GEMINI_GROQ_FILTERS_V1"
        AiFeature.DIAGNOSTIC -> "DIAGNOSTIC_GEMINI_GROQ_SAMPLE_V1"
    }

    private fun decision(obj: JSONObject, feature: AiFeature): Decision? {
        obj.optJSONObject("decisions")?.optJSONObject(feature.name)?.let {
            return Decision(it.optBoolean("allowed", false), it.optInt("disclosureVersion", 0),
                it.optLong("decidedAtEpochMillis", 0L).takeIf { time -> time > 0 },
                it.optString("scope", ""), it.optString("source", ""))
        }
        if (!obj.has(feature.name)) return null
        // Preserve old choices without inventing dates or claiming historical consent text.
        return Decision(obj.optBoolean(feature.name, false), obj.optInt("version", 0),
            null, "LEGACY_UNRECORDED", "legacy")
    }

    private val store = object : AiConsentStore {
        override suspend fun allowed(uid: String, feature: AiFeature) = withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                val current = try { decision(read(uid), feature) } catch (_: Exception) { null }
                current?.allowed == true && current.disclosureVersion == AI_DISCLOSURE_VERSION &&
                    current.scope == scope(feature)
            }
        }

        override suspend fun save(uid: String, feature: AiFeature, allowed: Boolean) = withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                check(directory.isDirectory || directory.mkdirs()) { t("AI seçimi kaydedilemedi.") }
                // Unreadable history is not overwritten; failure prevents transmission.
                val obj = read(uid)
                val decisions = obj.optJSONObject("decisions") ?: JSONObject().also { migrated ->
                    AiFeature.entries.forEach { oldFeature ->
                        decision(obj, oldFeature)?.let { old ->
                            migrated.put(oldFeature.name, JSONObject()
                                .put("allowed", old.allowed).put("disclosureVersion", old.disclosureVersion)
                                .put("scope", old.scope).put("source", old.source))
                        }
                    }
                }
                val previous = decision(obj, feature)
                if (!allowed && previous?.allowed == false) return@synchronized
                val event = JSONObject().put("allowed", allowed)
                    .put("disclosureVersion", AI_DISCLOSURE_VERSION)
                    .put("decidedAtEpochMillis", System.currentTimeMillis())
                    .put("scope", scope(feature))
                    .put("source", if (allowed) "feature_prompt" else "settings_revocation")
                val history = obj.optJSONArray("history") ?: JSONArray()
                history.put(JSONObject(event.toString()).put("feature", feature.name))
                decisions.put(feature.name, event)
                obj.put("storageVersion", 2).put("version", AI_DISCLOSURE_VERSION)
                    .put("decisions", decisions).put("history", history)
                // Keep legacy values for migration evidence; new authorization reads decisions first.
                val atomic = file(uid)
                val output = atomic.startWrite()
                try { output.write(obj.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
                catch (failure: Exception) { atomic.failWrite(output); throw failure }
            }
        }
    }

    val coordinator = AiConsentCoordinator(store)
    suspend fun latestDecision(uid: String, feature: AiFeature): Decision? = withContext(Dispatchers.IO) {
        synchronized(fileLock) { decision(read(uid), feature) }
    }
    suspend fun require(feature: AiFeature) {
        val uid = checkNotNull(AndroidBackend.auth.currentUser()?.id) { t("REAL hesabına giriş yap.") }
        coordinator.requireConsent(uid, feature) {
            check(AndroidBackend.auth.currentUser()?.id == uid) { t("Hesap değişti.") }
        }
    }
    suspend fun remove(uid: String) = withContext(Dispatchers.IO) { synchronized(fileLock) { file(uid).delete() } }
}
