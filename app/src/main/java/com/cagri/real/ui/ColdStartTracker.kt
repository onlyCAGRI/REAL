package com.cagri.real.ui

object ColdStartTracker {
    @Volatile
    var hasRunTransition: Boolean = false

    fun isEligible(reduceAnimations: Boolean): Boolean = !reduceAnimations && !hasRunTransition

    fun markExecuted() {
        hasRunTransition = true
    }

    fun resetForTesting() {
        hasRunTransition = false
    }
}
