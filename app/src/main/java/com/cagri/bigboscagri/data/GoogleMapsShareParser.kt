package com.cagri.bigboscagri.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder

/** Accept only Google Maps shares. Redirects are followed only to known HTTPS Maps hosts. */
object GoogleMapsShareParser {
    private val urlPattern = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val placePoint = Regex("!3d(-?\\d{1,2}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)")
    private val coordinatePair = Regex("^(-?\\d{1,2}(?:\\.\\d+)?),\\s*(-?\\d{1,3}(?:\\.\\d+)?)")
    private val cameraPoint = Regex("/@(-?\\d{1,2}(?:\\.\\d+)?),(-?\\d{1,3}(?:\\.\\d+)?)(?:,|/|$)")

    fun sharedUrl(text: String?): String? = urlPattern.find(text.orEmpty())?.value
        ?.trimEnd('.', ',', ';', ')', ']', '>', '"')?.takeIf(::isMapsUrl)

    fun parse(text: String, resolveShortLink: (String) -> String? = ::resolveRedirects): ReminderLocation? {
        val original = sharedUrl(text) ?: return null
        val originalUri = URI(original)
        val isShort = originalUri.host.equals("maps.app.goo.gl", true) ||
            originalUri.host.equals("goo.gl", true)
        val resolved = if (isShort) resolveShortLink(original)?.takeIf(::isMapsUrl) ?: original else original
        val uri = URI(resolved)
        val exactPoint = placePoint.find(resolved)?.let { it.groupValues[1] to it.groupValues[2] }
            ?: parseCoordinatesInQuery(uri)
        // A Maps camera center is not necessarily the place's pin; flag it as approximate.
        val camera = if (exactPoint == null && uri.path.orEmpty().startsWith("/maps/"))
            cameraPoint.find(resolved)?.let { it.groupValues[1] to it.groupValues[2] }
        else null
        val point = exactPoint ?: camera

        val latitude = point?.first?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
        val longitude = point?.second?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
        val lines = text.replace(original, "").lineSequence().map(String::trim)
            .filter { it.isNotBlank() && !it.equals("Google Maps", true) && !it.equals("Google Haritalar", true) }
            .toList()
        val pathName = Regex("/place/([^/?]+)").find(uri.rawPath.orEmpty())?.groupValues?.get(1)
            ?.let(::decode)?.replace('+', ' ')?.takeIf(String::isNotBlank)
        return ReminderLocation(
            placeName = lines.firstOrNull()?.take(150) ?: pathName?.take(150) ?: "Google Haritalar konumu",
            address = lines.drop(1).firstOrNull()?.take(250) ?: "",
            latitude = if (latitude != null && longitude != null) latitude else null,
            longitude = if (latitude != null && longitude != null) longitude else null,
            mapsUrl = original,
            approximateCoordinates = exactPoint == null && camera != null && latitude != null && longitude != null
        )
    }

    private fun parseCoordinatesInQuery(uri: URI): Pair<String, String>? = uri.rawQuery
        ?.split('&')?.firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=').lowercase()
            if (key !in setOf("q", "query", "ll", "center")) null
            else coordinatePair.find(decode(part.substringAfter('=', "")))?.let {
                it.groupValues[1] to it.groupValues[2]
            }
        }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value.replace("+", "%20"), "UTF-8")
    } catch (_: IllegalArgumentException) { value }

    private fun isMapsUrl(value: String): Boolean {
        return try {
            val uri = URI(value)
            val host = uri.host?.lowercase() ?: return false
            uri.scheme.equals("https", true) && uri.userInfo == null && uri.port == -1 &&
                (host == "maps.app.goo.gl" || host == "goo.gl" && uri.path.startsWith("/maps/") ||
                    host == "google.com" || host == "www.google.com" || host == "maps.google.com") &&
                (host != "google.com" && host != "www.google.com" || uri.path.startsWith("/maps"))
        } catch (_: Exception) { false }
    }

    private fun resolveRedirects(start: String): String? {
        var current = start
        repeat(4) {
            val connection = (URI(current).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 2500
                readTimeout = 2500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
            }
            try {
                val code = connection.responseCode
                if (code !in 300..399) return current
                val next = connection.getHeaderField("Location")?.let {
                    URI(current).resolve(it).toString()
                } ?: return current
                if (!isMapsUrl(next)) return current
                current = next
            } catch (_: Exception) { return current }
            finally { connection.disconnect() }
        }
        return current
    }
}
