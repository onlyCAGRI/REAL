package com.cagri.real.calendar

import com.cagri.real.i18n.t
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cagri.real.data.CustomCategory
import com.cagri.real.data.ReminderItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phone-calendar events filed under REAL categories ("Kategorize et"), shown as read-only cards inside those
 * categories. A card's id is the negative event id; it opens the same event window as the calendar does.
 */
internal data class CategorizedCalendarCards(
    /** Every upcoming event in the coming year as a card; customCategoryId is set when it is filed under a category. */
    val all: List<ReminderItem> = emptyList(),
    val occurrences: Map<Long, CalendarOccurrence> = emptyMap()
) {
    /** Only the events filed under a category. */
    val items: List<ReminderItem> get() = all.filter { it.customCategoryId != null }

    /** Events starting within the next [days] days (Home's "Takvim etkinlikleri"). */
    fun within(days: Int, now: Long = System.currentTimeMillis()): List<ReminderItem> {
        if (days <= 0) return emptyList()
        val until = now + days * 86_400_000L
        return all.filter { it.createdAt < until }
    }
}

/** Ayarlar → "Ana ekranda takvim": phone-calendar events on Home for the next 7 or 14 days, or off (0). */
internal object CalendarHomeDays {
    val options = listOf(0, 7, 14)
    private val state = mutableIntStateOf(-1)
    private fun prefs(context: android.content.Context) = com.cagri.real.platform.android.sync.SyncLocal.preferences(
        context.applicationContext, "real_calendar_home", android.content.Context.MODE_PRIVATE)
    fun get(context: android.content.Context): Int {
        if (state.intValue < 0) state.intValue = prefs(context).getInt("days", 0).takeIf { it in options } ?: 0
        return state.intValue
    }
    fun set(context: android.content.Context, days: Int) {
        prefs(context).edit().putInt("days", days).apply()
        state.intValue = days
    }
    fun label(days: Int) = if (days <= 0) t("Kapalı") else t("Önümüzdeki {0} gün", days)
}

/** Upcoming events of the coming year (a yearly birthday shows once), the nearest occurrence of each. */
@Composable
internal fun rememberCategorizedCalendarCards(reminders: List<ReminderItem>): CategorizedCalendarCards {
    val context = LocalContext.current.applicationContext
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val start = remember(today) { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val end = remember(today) { today.plusYears(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val read = rememberExternalCalendarEvents(start, end, reminders)
    val links = remember(context) { CalendarCategoryLinks(context) }
    val revision = CalendarCategoryLinks.revision.intValue
    return remember(read.events, revision) {
        val now = System.currentTimeMillis()
        fun localAllDay(value: Long) = Instant.ofEpochMilli(value).atZone(ZoneId.of("UTC"))
            .toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val upcoming = read.events
            .filter { o -> (if (o.allDay) localAllDay(o.end) else o.end) > now }
            .sortedBy { if (it.allDay) localAllDay(it.start) else it.start }
            .distinctBy { it.eventId }
        val items = upcoming.map { o ->
            val begin = if (o.allDay) localAllDay(o.start) else o.start
            ReminderItem(
                id = -o.eventId, text = o.title.ifBlank { o.calendarName }, createdAt = begin,
                alertAt = if (o.allDay) null else begin,
                plannedDateEpochDay = if (o.allDay) Instant.ofEpochMilli(begin).atZone(zone).toLocalDate().toEpochDay() else null,
                notifiedAlertAt = if (o.allDay) null else begin,
                sendImmediateNotification = false, customCategoryId = links.categoryFor(o.eventId, o.calendarId)
            )
        }
        CategorizedCalendarCards(items, upcoming.associateBy { -it.eventId })
    }
}

/**
 * The phone-calendar event window opened outside the calendar (from a category): categorize it, or add REAL
 * content to it ("REAL içeriği ekle"), which links a REAL card and opens it.
 */
@Composable
internal fun CalendarEventDetailHost(
    event: CalendarOccurrence,
    reminders: List<ReminderItem>,
    customCategories: List<CustomCategory>,
    reduceDynamicBlur: Boolean,
    /** (title, categoryId): the REAL card joins the event's category. */
    onCreateCalendarCard: suspend (String, Long?) -> Long?,
    onCreateCategory: (String) -> Unit,
    onOpenCard: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val links = remember(context) { CalendarCategoryLinks(context.applicationContext) }
    val store = remember(context) { CalendarStore(context.applicationContext) }
    val cards = remember(store) { CalendarCardLinks(store) }
    val revision = CalendarCategoryLinks.revision.intValue
    // A new category asked for here is linked once it exists (category creation returns no id).
    var pending by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(customCategories, pending) {
        val (name, whole) = pending ?: return@LaunchedEffect
        val created = customCategories.filter { it.name == name }.maxByOrNull { it.createdAt } ?: return@LaunchedEffect
        links.link(event.eventId, event.calendarId, created.id, whole)
        pending = null
    }
    val current = remember(event, revision) { links.categoryFor(event.eventId, event.calendarId) }
    ExternalCalendarEventDetail(
        occurrence = event,
        reduceDynamicBlur = reduceDynamicBlur,
        categories = customCategories,
        currentCategoryId = current,
        currentIsWholeCalendar = remember(event, revision) { current == null || links.isWholeCalendar(event.calendarId) },
        onCategorize = { categoryId, newName, whole ->
            when {
                categoryId != null -> links.link(event.eventId, event.calendarId, categoryId, whole)
                newName != null -> { pending = newName.take(60) to whole; onCreateCategory(newName.take(60)) }
                else -> links.link(event.eventId, event.calendarId, null, false)
            }
        },
        onDismiss = onDismiss,
        onCreateRealCard = {
            val provider = DeviceCalendarProvider(context.applicationContext)
            val source = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                check(store.isCurrent())
                provider.calendars().singleOrNull { it.id == event.calendarId &&
                    store.options().readIdentities[it.id] == it.identity }
                    ?: error(t("Kaynak takvim değişti; seçimi yenile."))
            }
            val existing = cards.associations().firstOrNull {
                it.eventId == event.eventId && it.calendarId == event.calendarId && it.identity == source.identity &&
                    reminders.any { r -> r.id == it.cardId }
            }
            val id = existing?.cardId ?: onCreateCalendarCard(event.title.take(2000), current) ?: error(t("REAL kartı oluşturulamadı."))
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                check(store.isCurrent())
                if (existing == null) cards.associate(event, source, id)
            }
            onDismiss()
            onOpenCard(id)
        }
    )
}
