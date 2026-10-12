package com.cagri.real.share

private val shareIdPattern = Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
fun isSharedCardId(value: String): Boolean = shareIdPattern.matches(value)

/** Exact handoff only: no remote host, arbitrary URL, traversal, query or extra path segment. */
fun sharedCardIdFromLink(value: String?): String? = value
    ?.takeIf { it.startsWith("real://shared/") }
    ?.removePrefix("real://shared/")
    ?.takeIf(::isSharedCardId)
