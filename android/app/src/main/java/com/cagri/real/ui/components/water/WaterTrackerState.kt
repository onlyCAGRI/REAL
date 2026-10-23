package com.cagri.real.ui.components.water

import com.cagri.real.i18n.t
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cagri.real.data.WaterTrackerRepository
import com.cagri.real.data.WaterTrackerSnapshot
import com.cagri.real.data.BeverageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

@Stable
internal class WaterTrackerState(
    private val repository: WaterTrackerRepository,
    private val scope: CoroutineScope
) {
    var snapshot by mutableStateOf(WaterTrackerSnapshot())
        private set
    var day by mutableStateOf(LocalDate.now())
        private set
    var zone by mutableStateOf(ZoneId.systemDefault())
        private set
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private val mutex = Mutex()
    val todayEntries by derivedStateOf { snapshot.entriesOn(day, zone) }
    val todayMl by derivedStateOf { totalFor(BeverageType.WATER) }
    val progress by derivedStateOf { progressFor(BeverageType.WATER).coerceIn(0f, 1f) }

    fun totalFor(beverage: BeverageType): Long = todayEntries.filter { it.beverage == beverage }.sumOf { it.amountMl.toLong() }

    fun goalFor(beverage: BeverageType): Int =
        if (beverage == BeverageType.WATER) snapshot.goalMl else beverage.visualGoalMl

    fun progressFor(beverage: BeverageType): Float {
        val goal = goalFor(beverage).coerceAtLeast(1)
        return totalFor(beverage).toFloat() / goal.toFloat()
    }

    suspend fun refresh() = mutex.withLock {
        zone = ZoneId.systemDefault()
        day = LocalDate.now(zone)
        try {
            snapshot = repository.load()
            ready = true
            error = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            ready = false
            error = t("İçecek kayıtları okunamadı. Yeniden dene.")
        }
    }

    fun add(amount: Int, beverage: BeverageType = BeverageType.WATER) = write { repository.add(amount, beverage) }
    fun remove(id: String) = write { repository.remove(id) }
    fun goal(amount: Int) = write { repository.setGoal(amount) }
    fun retry() { scope.launch { refresh() } }

    private fun write(action: suspend () -> WaterTrackerSnapshot) {
        if (!ready || busy) return
        busy = true
        scope.launch {
            try {
                mutex.withLock {
                    snapshot = action()
                    zone = ZoneId.systemDefault()
                    day = LocalDate.now(zone)
                    error = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Kaydedilemedi. Yeniden dene." }
            finally { busy = false }
        }
    }
}

@Composable
internal fun rememberWaterTrackerState(): WaterTrackerState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val state = remember(context, scope) {
        WaterTrackerState(WaterTrackerRepository(com.cagri.real.platform.android.sync.SyncLocal.preferences(context,
            WaterTrackerRepository.PREFERENCES_NAME, Context.MODE_PRIVATE
        )), scope)
    }
    LaunchedEffect(state, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.refresh()
                // Refresh at midnight, timezone changes and foreground entry without an alarm/service.
                val now = java.time.ZonedDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(minOf(60_000L, java.time.Duration.between(now, midnight).toMillis().coerceAtLeast(100L)))
            }
        }
    }
    return state
}
