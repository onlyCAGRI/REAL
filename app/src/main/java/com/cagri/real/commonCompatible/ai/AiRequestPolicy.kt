package com.cagri.real.ai

/** Exact paths; catalog lookup/public share/sync and health metadata are not generative requests. */
fun aiFeatureForPath(path: String): AiFeature? = when(path) {
    "/api/voice-intent" -> AiFeature.VOICE
    "/api/mood-suggestions" -> AiFeature.MOOD
    "/api/media-recommendations", "/api/game-recommendations" -> AiFeature.DISCOVERY
    "/api/ai-diagnostic" -> AiFeature.DIAGNOSTIC
    else -> null
}
