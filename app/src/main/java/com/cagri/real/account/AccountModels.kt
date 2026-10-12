package com.cagri.real.account

enum class ErasureMode { CLOUD, ACCOUNT }
data class CloudUsage(val usedBytes: Long, val activeBytes: Long, val retainedBytes: Long,
    val quotaBytes: Long, val fileCount: Int, val measuredAt: Long)
data class ErasureStatus(val operationId: String, val mode: ErasureMode, val phase: String) {
    val complete: Boolean get() = phase == "COMPLETE"
}
data class AccountSnapshot(val usage: CloudUsage?, val erasure: ErasureStatus?, val backupDeviceApproved: Boolean = true)
interface AccountRepository {
    fun cachedUsage(): CloudUsage?
    suspend fun refresh(): AccountSnapshot
    suspend fun start(mode: ErasureMode, operationId: String): ErasureStatus
    suspend fun advance(operationId: String): ErasureStatus
}
