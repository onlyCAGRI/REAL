package com.cagri.bigboscagri.ui

object ColdStartTracker {
    @Volatile
    var hasRunTransition: Boolean = false

    fun isEligible(reduceAnimations: Boolean): Boolean {
        if (reduceAnimations || hasRunTransition) return false
        return true
    }

    fun markExecuted() {
        hasRunTransition = true
    }

    fun resetForTesting() {
        hasRunTransition = false
    }
}
