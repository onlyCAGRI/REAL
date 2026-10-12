package com.cagri.real.platform.android.sync

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.*

class RealSyncJobService:JobService() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val jobs=mutableMapOf<Int,Job>()
    override fun onStartJob(params:JobParameters):Boolean {
        jobs[params.jobId]=scope.launch {
            val retry=try {
                val requestGeneration = if (params.jobId == 8101 && params.extras.containsKey("real_request_generation"))
                    params.extras.getLong("real_request_generation") else null
                SyncLocal.sync(requestGeneration, params.extras.getString("real_request_session"));false
            } catch(cancelled:CancellationException) { throw cancelled } catch(_:Exception) { true }
            withContext(Dispatchers.Main) { jobs.remove(params.jobId);jobFinished(params,retry) }
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean { jobs.remove(params.jobId)?.cancel();return true }
    override fun onDestroy() { scope.cancel();super.onDestroy() }
}
