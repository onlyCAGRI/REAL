package com.cagri.real.ui.subscription

/** Presentation only. These values never grant access to a paid feature. */
internal enum class ProCatalogState { NotConfigured, Loading, Ready, Unavailable }
internal enum class ProCheckoutState { Idle, OpeningStore, Pending, Verifying, Restoring }

/**
 * Populate from Play ProductDetails, not a price table in the app.
 * key maps to fresh ProductDetails + offerToken in the future billing coordinator.
 * termsText must describe EVERY pricing phase, duration, renewal and cancellation.
 */
internal data class ProStoreOffer(
    val key: String,
    val title: String,
    val priceText: String,
    val termsText: String
) {
    val isComplete: Boolean get() = listOf(key, title, priceText, termsText).all { it.isNotBlank() }
}

internal data class ProStoreUiState(
    val catalog: ProCatalogState = ProCatalogState.NotConfigured,
    val offers: List<ProStoreOffer> = emptyList(),
    val checkout: ProCheckoutState = ProCheckoutState.Idle,
    // Only the verified, signed-in account's server entitlement may set this.
    val verifiedProActive: Boolean = false,
    val storeSubscription: Boolean = false,
    val termsUrl: String? = null,
    // User-facing, localized error/status copy; never raw Billing/HTTP diagnostics.
    val notice: String? = null
) {
    val canRestore: Boolean get() = catalog == ProCatalogState.Ready && checkout == ProCheckoutState.Idle

    fun purchasableOffer(key: String?): ProStoreOffer? {
        if (catalog != ProCatalogState.Ready || checkout != ProCheckoutState.Idle || verifiedProActive) return null
        if (!isSecureDocumentUrl(termsUrl)) return null
        // Duplicate keys are ambiguous: fail closed rather than charging the wrong offer.
        return offers.singleOrNull { it.key == key }?.takeIf { it.isComplete }
    }
}

internal fun isSecureDocumentUrl(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    return runCatching {
        val uri = java.net.URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
