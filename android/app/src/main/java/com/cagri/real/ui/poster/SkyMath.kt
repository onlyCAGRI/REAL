package com.cagri.real.ui.poster

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Low-precision sky positions for the sky poster: good to a fraction of a degree, which is far below one star dot
 * on the poster. Star coordinates are J2000; precession since then (~0.4°) is ignored on purpose.
 */
object SkyMath {
    private const val RAD = Math.PI / 180.0

    /** Julian date of a Unix time in milliseconds. */
    fun julian(epochMillis: Long): Double = epochMillis / 86_400_000.0 + 2_440_587.5

    /** Local sidereal time in degrees. */
    fun localSidereal(jd: Double, longitude: Double): Double =
        norm(280.46061837 + 360.98564736629 * (jd - 2_451_545.0) + longitude)

    /** Altitude and azimuth (degrees; azimuth from north towards east) of an RA/Dec point. */
    fun altAz(raDeg: Double, decDeg: Double, latitude: Double, lstDeg: Double): Pair<Double, Double> {
        val h = (lstDeg - raDeg) * RAD
        val d = decDeg * RAD
        val p = latitude * RAD
        val alt = asin(sin(p) * sin(d) + cos(p) * cos(d) * cos(h))
        val az = atan2(-sin(h) * cos(d), cos(p) * sin(d) - sin(p) * cos(d) * cos(h))
        return alt / RAD to norm(az / RAD)
    }

    /** The Moon's RA/Dec (degrees), its lit fraction 0..1, and whether it is waxing. */
    data class Moon(val ra: Double, val dec: Double, val illumination: Double, val waxing: Boolean)

    fun moon(jd: Double): Moon {
        val d = jd - 2_451_545.0
        val l0 = 218.316 + 13.176396 * d
        val m = 134.963 + 13.064993 * d
        val f = 93.272 + 13.229350 * d
        val lambda = norm(l0 + 6.289 * sin(m * RAD))
        val beta = 5.128 * sin(f * RAD)
        val (ra, dec) = eclipticToEquatorial(lambda, beta, d)
        val g = 357.528 + 0.9856003 * d
        val sun = norm(280.460 + 0.9856474 * d + 1.915 * sin(g * RAD) + 0.020 * sin(2 * g * RAD))
        val elongation = norm(lambda - sun)
        return Moon(ra, dec, (1 - cos(elongation * RAD)) / 2, elongation < 180)
    }

    /** The Sun's altitude in degrees: above 0 means it is daytime at the place. */
    fun sunAltitude(epochMillis: Long, latitude: Double, longitude: Double): Double {
        val jd = julian(epochMillis)
        val d = jd - 2_451_545.0
        val g = 357.528 + 0.9856003 * d
        val lambda = norm(280.460 + 0.9856474 * d + 1.915 * sin(g * RAD) + 0.020 * sin(2 * g * RAD))
        val (ra, dec) = eclipticToEquatorial(lambda, 0.0, d)
        return altAz(ra, dec, latitude, localSidereal(jd, longitude)).first
    }

    private fun eclipticToEquatorial(lambdaDeg: Double, betaDeg: Double, d: Double): Pair<Double, Double> {
        val e = (23.439 - 0.0000004 * d) * RAD
        val l = lambdaDeg * RAD
        val b = betaDeg * RAD
        val ra = atan2(sin(l) * cos(e) - Math.tan(b) * sin(e), cos(l))
        val dec = asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))
        return norm(ra / RAD) to dec / RAD
    }

    /** Turkish key for the phase name, shown through t(). */
    fun phaseName(moon: Moon): String = when {
        moon.illumination < 0.03 -> "Yeni ay"
        moon.illumination > 0.97 -> "Dolunay"
        moon.illumination in 0.45..0.55 -> if (moon.waxing) "İlk dördün" else "Son dördün"
        moon.illumination < 0.5 -> if (moon.waxing) "Hilal" else "Balzamik hilal"
        else -> if (moon.waxing) "Büyüyen ay" else "Küçülen ay"
    }

    private fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
}
