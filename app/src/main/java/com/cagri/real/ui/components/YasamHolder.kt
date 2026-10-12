package com.cagri.real.ui.components

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cagri.real.books.Book
import com.cagri.real.books.BookShelfStore
import com.cagri.real.data.BeverageType
import com.cagri.real.data.JournalMood
import com.cagri.real.data.WaterTrackerRepository
import com.cagri.real.data.WaterTrackerSnapshot
import com.cagri.real.discovery.GameDiscoveryRepository
import com.cagri.real.discovery.MediaDiscoveryRepository
import com.cagri.real.platform.android.sync.SyncLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

internal data class YasamWaterDay(val day: LocalDate, val amountMl: Int, val goalMl: Int? = null)

internal data class YasamWater(val todayMl: Int, val goalMl: Int, val yesterdayMl: Int,
    val history: List<YasamWaterDay> = emptyList()) {
    val progress: Float get() = (todayMl.toFloat() / goalMl.coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal data class YasamBook(val book: Book, val percent: Int?)

/** Mood ile ilgili, ekran başına bir kez hesaplanan türetilmiş değerler. */
internal data class YasamMoodView(
    val last: JournalMood?,
    val yesterday: JournalMood?,
    val points: List<Float>
)

/**
 * NOTLAR
 * - Kitap yüzdesi = page / pageCount (BookShelfStore.page). EPUB'da "page" bölüm indeksi olduğu için
 *   bu oranın doğruluğu DOĞRULANMADI; yanlışsa percentOf güncellenmeli.
 * - "Son okunan" bilgisi eskiden tutulmuyordu: BookShelfStore.lastReadId() yeni eklendi. Eski
 *   kullanıcılarda ilk okumaya kadar boştur; o zaman en ilerideki kitap seçilir (loadBook).
 *
 * Yaşam ekranının okuma/yazma durumu. Veri yalnız [load] ile (bir kez) ve +250 ml eyleminde değişir;
 * composition sırasında IO yok, polling yok.
 */
@Stable
internal class YasamHolder(context: Context, uid: String, private val scope: CoroutineScope) {
    private val waterRepository = WaterTrackerRepository(
        SyncLocal.preferences(context, WaterTrackerRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
    )
    private val shelf = BookShelfStore(context, uid)
    // Yalnız okunur: kayıtlı izlenecek / oynanacak liste sayıları (repository'lere yazılmaz). Misafirde yok.
    private val mediaRepository = if (uid == "guest") null else MediaDiscoveryRepository(context, uid)
    private val gameRepository = if (uid == "guest") null else GameDiscoveryRepository(context, uid)
    private var adding = false

    var water by mutableStateOf<YasamWater?>(null)
        private set
    var book by mutableStateOf<YasamBook?>(null)
        private set
    /** İzlenecek listem / oynanacak listem kayıt sayısı; okunamazsa null (etikette sayı yazılmaz). */
    var watchlistCount by mutableStateOf<Int?>(null)
        private set
    var playlistCount by mutableStateOf<Int?>(null)
        private set

    suspend fun load() {
        loadWater()
        loadBook()
        loadDiscoveryCounts()
    }

    private suspend fun loadDiscoveryCounts() {
        watchlistCount = try {
            mediaRepository?.load()?.later?.size
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        playlistCount = try {
            gameRepository?.load()?.list?.size
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    /** Mevcut WaterTrackerRepository.add çağrısı; hedef/geçmiş mantığına dokunmaz. */
    fun addWater(amountMl: Int = 250) {
        if (adding || water == null) return
        adding = true
        scope.launch {
            try {
                water = toWater(waterRepository.add(amountMl))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Kayıt başarısızsa kutu eski değerde kalır; tam ekran Su ekranı hatayı zaten gösterir.
            } finally {
                adding = false
            }
        }
    }

    private suspend fun loadWater() {
        water = try {
            toWater(waterRepository.load())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadBook() {
        book = try {
            val all = shelf.load()
            val lastId = shelf.lastReadId()
            var pick = all.firstOrNull { it.id == lastId }
            var page = pick?.let { shelf.page(it) } ?: 0
            if (pick == null) {
                // Eski kullanıcılarda "son okunan" kaydı yoktur: en ilerideki kitabı al.
                val best = all.map { it to shelf.page(it) }.filter { it.second > 0 }.maxByOrNull { it.second }
                pick = best?.first
                page = best?.second ?: 0
            }
            pick?.let { YasamBook(it, percentOf(page, it.pageCount)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun percentOf(page: Int, pageCount: Int?): Int? {
        val total = pageCount ?: return null
        if (total <= 0 || page <= 0) return null
        return (page * 100f / total).roundToInt().coerceIn(1, 100)
    }

    private fun toWater(snapshot: WaterTrackerSnapshot): YasamWater {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val totals = snapshot.entries.asSequence().filter { it.beverage == BeverageType.WATER }
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .mapValues { (_, entries) -> entries.sumOf { it.amountMl } }
        val history = (19 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            YasamWaterDay(day, totals[day] ?: 0, snapshot.dailyGoals[day.toEpochDay()])
        }
        return YasamWater(totals[today] ?: 0, snapshot.goalMl, totals[today.minusDays(1)] ?: 0, history)
    }
}
