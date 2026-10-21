package com.cagri.real.speech

import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MusicBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** All requested content is resolved before category creation or any card write. */
data class PreparedVoiceIntent(
    val intent: GeminiVoiceIntent,
    val schedule: VoiceCreationSchedule,
    val musicBlocks: List<MusicBlock>,
    val mediaBlocks: List<MediaBlock>
)

suspend fun prepareVoiceCreation(intents: List<GeminiVoiceIntent>, nowMillis: Long): List<PreparedVoiceIntent> = coroutineScope {
    val permits = Semaphore(3)
    intents.map { intent -> async {
        val schedule = resolveVoiceCreationSchedule(intent, nowMillis)
        require(intents.size == 1 || !schedule.needsTime) {
            "Birden fazla kartın tekrar/saat bilgisi eksik. Kartları saatleriyle yeniden söyle; henüz kaydedilmediler."
        }
        val (music, media) = permits.withPermit { resolveVoiceContentBlocks(intent) }
        PreparedVoiceIntent(intent, schedule, music, media)
    } }.awaitAll()
}
