package com.cagri.real.data

data class CustomCategory(
    val id: Long,
    val name: String,
    val iconKey: String,
    val accentStart: Int,
    val accentEnd: Int? = null,
    val hideFromGeneralLists: Boolean = false,
    val useDarkText: Boolean = false,
    val gradientBlendFraction: Float? = null,
    val customSolidColorArgb: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Ortak kategori: the shared category id on the server; null = a private category. */
    val sharedId: String? = null,
    /** "owner", "full" (add / edit / complete / delete) or "view" (read only). */
    val sharedRole: String? = null,
    val sharedOwnerName: String? = null
) {
    val isShared: Boolean get() = sharedId != null
    /** Someone else's category on this phone (it never enters this account's personal sync). */
    val isSharedMirror: Boolean get() = sharedId != null && sharedRole != null && sharedRole != "owner"
    val isReadOnlyShared: Boolean get() = sharedRole == "view"
}
