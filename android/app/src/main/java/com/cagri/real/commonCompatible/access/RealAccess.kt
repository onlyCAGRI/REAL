package com.cagri.real.access

/** Stable wire keys shared with Worker and a future iOS client. Not UI labels. */
enum class RealFeature(val wireKey: String) {
    VoiceAi("voice.ai"), MoodAi("mood.ai"), MediaDiscovery("discover.media"),
    GameDiscovery("discover.games"), PlannerGoals("planner.goals"),
    CardColors("card.colors"), CategoryGradients("category.gradients")
}

data class RealAccessSnapshot(
    val uid: String,
    val plan: String,
    val source: String,
    val tester: Boolean,
    val expiresAt: Long?,
    val serverTime: Long,
    val cacheUntil: Long,
    val rollout: String,
    val features: Set<RealFeature>,
    val effectiveFeatures: Set<RealFeature>,
    val aiUsed: Int,
    val aiLimit: Int,
    val aiResetAt: Long
) {
    // OBSERVE is a backend rollout setting, not an entitlement. Free UI stays locked.
    // The server still checks every remote AI request. Never use this as proof of payment.
    fun allows(feature: RealFeature): Boolean = isPro && feature in features
    val isPro: Boolean get() = plan == "PRO"
}
