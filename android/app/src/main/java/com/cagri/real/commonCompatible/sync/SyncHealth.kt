package com.cagri.real.sync

import com.cagri.real.i18n.t

/** STORAGE_FULL: the account's cloud space is full; the file stays on this phone and goes up once there is room. */
enum class SyncBinaryCode { LOCAL_FILE_UNAVAILABLE, FILE_TOO_LARGE, EMPTY_FILE, REMOTE_FILE_UNAVAILABLE, TRANSFER_PENDING, IO_FAILURE, INVALID_BINARY, STORAGE_FULL }
enum class SyncBinaryDirection { UPLOAD, DOWNLOAD }
/** Only expected per-file failures may be isolated. Auth/account/DB/cancellation errors propagate. */
class SyncBinaryFailure(val blobId: String, val code: SyncBinaryCode, val automaticRetry: Boolean,
    cause: Throwable? = null) : Exception(t("Kartın bir eki eşitlenemedi. İçerik korunuyor; Hesapları yönet → Eşitleme bölümünden tekrar deneyebilirsin."), cause)
data class SyncBinaryIssue(val recordId: String, val key: String, val title: String,
    val blobId: String, val direction: SyncBinaryDirection, val code: SyncBinaryCode,
    val automaticRetry: Boolean, val generation: Long, val token: String)
/** A record the account refused ([status] / [code]); it waits until it changes again or the person chooses. */
data class SyncRejectedRecord(val recordId: String, val kind: String, val key: String, val title: String,
    val status: Int, val code: String?, val generation: Long, val hasCloudCopy: Boolean)
data class SyncHealthSnapshot(val workspace: String, val status: SyncStatus,
    val issues: List<SyncBinaryIssue>, val conflicts: List<PersistedSyncConflict>, val paused: Boolean,
    val rejected: List<SyncRejectedRecord> = emptyList())

/** Returns the branch for the original ID. A copy must get a NEW identity in the store transaction. */
fun conflictResolutionPayload(conflict: PersistedSyncConflict, expectedRevision: Long,
    expectedGeneration: Long, choice: SyncConflictChoice): String? {
    check(conflict.revision == expectedRevision && conflict.generation == expectedGeneration) {
        t("Sürüm değişti; yeniden yükleyip seç.")
    }
    val chosen = when (choice) {
        SyncConflictChoice.CURRENT_LOCAL -> conflict.currentLocal
        SyncConflictChoice.SAVED_LOCAL -> conflict.savedLocal
        SyncConflictChoice.REMOTE -> conflict.remote
        SyncConflictChoice.LOCAL_AS_NEW_CARD -> {
            check(conflict.kind == "CARD" && conflict.currentLocal != null) { t("Yalnız yerel kart kopyalanabilir.") }
            conflict.remote
        }
    }
    check(conflict.remote != null || conflict.revision == 0L || chosen == null) {
        t("Bulutta silinen kart aynı kimlikle geri oluşturulamaz; yereli yeni kart olarak sakla.")
    }
    return chosen
}
