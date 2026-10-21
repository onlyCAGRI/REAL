package com.cagri.real.share

import java.net.URI

/** Public URLs come from the authenticated sharing API's PUBLIC_SHARE_BASE_URL.
 * Never derive them from the private AndroidBackend origin. Legacy HTTPS hosts
 * remain valid: changing the public hostname must not invalidate saved links.
 */
fun validatedPublicShareUrl(id: String, value: String): String? {
    if (!isSharedCardId(id)) return null
    return runCatching {
        val uri = URI(value)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port != 443) || uri.rawPath != "/s/$id" ||
            uri.rawQuery != null || uri.rawFragment != null) null
        else uri.toASCIIString()
    }.getOrNull()
}
