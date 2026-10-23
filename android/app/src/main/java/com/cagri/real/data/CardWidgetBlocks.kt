package com.cagri.real.data

import com.cagri.real.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/*
 * Counter and market blocks are stored like REAL's text boxes: a TEXT_SNIPPET attachment with its own
 * sourceTitle. Sync, ordering, the free grid and resizing therefore work with no new card field, and
 * anywhere a card is shown as plain data the content stays readable ("33", "Bitcoin").
 */

const val REAL_COUNTER_SOURCE = "REAL_COUNTER"
const val REAL_MARKET_SOURCE = "REAL_MARKET"
private const val COUNTER_MIME_PREFIX = "application/x-real-counter;steps="
private const val MARKET_MIME = "application/x-real-market"
private val DEFAULT_COUNTER_STEPS = listOf(-1, 1)
const val COUNTER_MAX_STEPS = 6

data class CounterConfig(val title: String, val value: Long, val steps: List<Int>)

fun ReminderAttachment.isCounterBlock(): Boolean = type == AttachmentType.TEXT_SNIPPET && sourceTitle == REAL_COUNTER_SOURCE
fun ReminderAttachment.isMarketBlock(): Boolean = type == AttachmentType.TEXT_SNIPPET && sourceTitle == REAL_MARKET_SOURCE

fun ReminderAttachment.counterConfig(): CounterConfig = CounterConfig(
    title = displayName?.takeIf { it.isNotBlank() } ?: t("Sayaç"),
    value = text?.trim()?.toLongOrNull() ?: 0L,
    steps = normalizedCounterSteps(mimeType?.removePrefix(COUNTER_MIME_PREFIX)?.split(',')?.mapNotNull { it.trim().toIntOrNull() })
)

fun normalizedCounterSteps(steps: List<Int>?): List<Int> =
    steps.orEmpty().filter { it != 0 && it in -9999..9999 }.distinct().sorted().take(COUNTER_MAX_STEPS)
        .ifEmpty { DEFAULT_COUNTER_STEPS }

fun ReminderAttachment.withCounter(config: CounterConfig): ReminderAttachment = copy(
    displayName = config.title.trim().take(40).ifBlank { "Sayaç" },
    text = config.value.coerceIn(-999_999_999L, 999_999_999L).toString(),
    mimeType = COUNTER_MIME_PREFIX + normalizedCounterSteps(config.steps).joinToString(",")
)

fun newCounterAttachment(): ReminderAttachment = ReminderAttachment(
    id = UUID.randomUUID().toString(), type = AttachmentType.TEXT_SNIPPET,
    mimeType = COUNTER_MIME_PREFIX + DEFAULT_COUNTER_STEPS.joinToString(","),
    displayName = "Sayaç", text = "0", sourceTitle = REAL_COUNTER_SOURCE
)

enum class MarketAsset(val id: String, val label: String, val symbol: String) {
    BTC("btc", "Bitcoin", "BTC"),
    GOLD_GRAM("gold_gram", "Gram altın", "GR"),
    EUR("eur", "Euro", "EUR"),
    USD("usd", "Dolar", "USD");

    companion object {
        fun of(id: String?): MarketAsset = entries.firstOrNull { it.id == id?.trim() } ?: BTC
    }
}

fun ReminderAttachment.marketAsset(): MarketAsset = MarketAsset.of(text)

fun ReminderAttachment.withMarketAsset(asset: MarketAsset): ReminderAttachment =
    copy(displayName = asset.label, text = asset.id, mimeType = MARKET_MIME)

fun newMarketAttachment(asset: MarketAsset = MarketAsset.BTC): ReminderAttachment = ReminderAttachment(
    id = UUID.randomUUID().toString(), type = AttachmentType.TEXT_SNIPPET, mimeType = MARKET_MIME,
    displayName = asset.label, text = asset.id, sourceTitle = REAL_MARKET_SOURCE
)

data class MarketQuote(
    val id: String, val name: String, val price: Double, val change24h: Double?,
    val sparkline: List<Double>, val updatedAt: Long, val stale: Boolean
)

/**
 * Prices straight from CoinGecko's public API (the worker's network is refused there). Four small requests
 * cover all four tiles; the shared state means one refresh serves every market block on screen.
 */
object RealMarketClient {
    private const val API = "https://api.coingecko.com/api/v3/coins"
    private const val REFRESH_MS = 4 * 60 * 1000L
    private const val TROY_OUNCE_GRAMS = 31.1034768
    private const val SPARK_POINTS = 25
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, MarketQuote>>(emptyMap())
    val quotes: StateFlow<Map<String, MarketQuote>> = state.asStateFlow()
    @Volatile private var fetchedAt = 0L

    /** 24 hours of ~5 minute points priced in [vs]: [[timeMs, price], ...] oldest first. */
    private fun history(coin: String, vs: String): List<Pair<Long, Double>> {
        val connection = (java.net.URL("$API/$coin/market_chart?vs_currency=$vs&days=1").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode} from CoinGecko")
            val prices = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("prices")
                ?: throw IOException("CoinGecko prices missing")
            return buildList {
                for (i in 0 until prices.length()) {
                    val row = prices.optJSONArray(i) ?: continue
                    val price = row.optDouble(1, Double.NaN)
                    if (price.isFinite()) add(row.optLong(0) to price)
                }
            }.also { if (it.size < 2) throw IOException("CoinGecko history too short") }
        } finally {
            connection.disconnect()
        }
    }

    private fun ratio(a: List<Pair<Long, Double>>, b: List<Pair<Long, Double>>): List<Double> {
        var j = 0
        return a.mapNotNull { (t, x) ->
            while (j + 1 < b.size && kotlin.math.abs(b[j + 1].first - t) <= kotlin.math.abs(b[j].first - t)) j++
            b[j].second.takeIf { it > 0 }?.let { x / it }
        }
    }

    private fun quote(id: String, name: String, values: List<Double>, now: Long): MarketQuote {
        val step = if (values.size <= SPARK_POINTS) 1.0 else (values.size - 1).toDouble() / (SPARK_POINTS - 1)
        val spark = if (values.size <= SPARK_POINTS) values else List(SPARK_POINTS) { values[Math.round(it * step).toInt()] }
        val first = values.first(); val last = values.last()
        return MarketQuote(id, name, last, if (first > 0) (last / first - 1) * 100 else null, spark, now, stale = false)
    }

    private val series = mutableMapOf<String, Pair<Long, List<Pair<Long, Double>>>>()

    /** One CoinGecko series, reused for a few minutes so Euro and Dolar share the BTC/TRY request. */
    private fun cachedHistory(coin: String, vs: String, force: Boolean): List<Pair<Long, Double>> {
        val key = "$coin/$vs"
        val hit = series[key]
        if (!force && hit != null && System.currentTimeMillis() - hit.first < REFRESH_MS) return hit.second
        return history(coin, vs).also { series[key] = System.currentTimeMillis() to it }
    }

    /**
     * Fetches only what the shown tiles need (Bitcoin / gram altın: one request, Euro / Dolar: two).
     * A failed refresh keeps the last prices on screen.
     */
    suspend fun refresh(assets: Set<MarketAsset>, force: Boolean = false): Result<Unit> = mutex.withLock {
        val now = System.currentTimeMillis()
        val due = assets.filter { asset ->
            force || state.value[asset.id]?.let { now - it.updatedAt >= REFRESH_MS } != false
        }
        if (due.isEmpty()) return@withLock Result.success(Unit)
        runCatching {
            withContext(Dispatchers.IO) {
                due.associate { asset ->
                    asset.id to when (asset) {
                        MarketAsset.BTC -> quote("btc", "Bitcoin", cachedHistory("bitcoin", "try", force).map { it.second }, now)
                        MarketAsset.GOLD_GRAM -> quote("gold_gram", "Gram altın",
                            cachedHistory("pax-gold", "try", force).map { it.second / TROY_OUNCE_GRAMS }, now) // PAXG = 1 troy ounce
                        MarketAsset.EUR -> quote("eur", "Euro", ratio(cachedHistory("bitcoin", "try", force), cachedHistory("bitcoin", "eur", force)), now)
                        MarketAsset.USD -> quote("usd", "Dolar", ratio(cachedHistory("bitcoin", "try", force), cachedHistory("bitcoin", "usd", force)), now)
                    }
                }
            }
        }.map { fresh -> state.value = state.value + fresh; fetchedAt = now }
    }
}
