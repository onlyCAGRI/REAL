package com.cagri.real.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.cagri.real.data.CardBlockPlacement
import com.cagri.real.data.CardGameBlock
import com.cagri.real.data.CategoryRepository
import com.cagri.real.data.ChecklistItem
import com.cagri.real.data.JournalMood
import com.cagri.real.data.MediaBlock
import com.cagri.real.data.MediaType
import com.cagri.real.data.MoodDetails
import com.cagri.real.data.MoodScope
import com.cagri.real.data.MoodTrackerRepository
import com.cagri.real.data.MusicBlock
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderItem
import com.cagri.real.data.ReminderJson
import com.cagri.real.data.ReminderLocation
import com.cagri.real.platform.android.sync.SyncLocal
import java.time.LocalDate
import java.time.ZoneId

/**
 * DEBUG BUILDS ONLY (src/debug): fills REAL with English sample content for promo screenshots, one persona at a time.
 * adb shell am start -n com.cagri.real/com.cagri.real.demo.DemoSeedActivity --es persona lawyer|teacher|student|friends
 * Replaces this device's local cards, categories and moods. Card ids are logged under the tag "RealDemo".
 * Never part of release builds.
 */
class DemoSeedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val persona = intent.getStringExtra("persona") ?: "lawyer"
        kotlinx.coroutines.runBlocking { SyncLocal.awaitInitialized() }
        seed(persona)
        finish()
    }

    private class Cat(val name: String, val icon: String, val color: Int)
    private class Spec(val text: String, val cat: String?, val build: ReminderItem.(Ctx) -> ReminderItem = { this })
    private class Ctx(val now: Long, val at: (Long, Int, Int) -> Long, val day: (Long) -> Long, val check: (List<Pair<String, Boolean>>) -> List<ChecklistItem>)

    private fun seed(persona: String) {
        val ctx = applicationContext
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var checkId = now
        val c = Ctx(
            now = now,
            at = { d, h, m -> today.plusDays(d).atTime(h, m).atZone(zone).toInstant().toEpochMilli().coerceAtLeast(now + 3_600_000) },
            day = { d -> today.plusDays(d).toEpochDay() },
            check = { items -> items.map { (t, done) -> ChecklistItem(id = checkId++, text = t, completedAt = if (done) now - 3_600_000 else null) } }
        )
        fun ReminderItem.timed(d: Long, h: Int, m: Int = 0) = c.at(d, h, m).let { copy(alertAt = it, anchorAlertAt = it) }
        fun ReminderItem.done(hoursAgo: Long = 20) = copy(completedAt = now - hoursAgo * 3_600_000)
        fun ReminderItem.list(vararg items: Pair<String, Boolean>) = copy(isChecklist = true, checklistItems = c.check(items.toList()))
        fun ReminderItem.place(name: String, address: String = "") = copy(location = ReminderLocation(placeName = name, address = address))

        SyncLocal.preferences(ctx, "real_ui", MODE_PRIVATE).edit()
            .putBoolean("onboarding_completed", true).putBoolean("first_home_intro_completed", true)
            .putString("user_display_name", "Alex").commit()

        val (cats, specs) = when (persona) {
            "teacher" -> listOf(Cat("Class 9B", "SCHOOL", 8), Cat("Class 11A", "SCHOOL", 2), Cat("Grading", "FILE", 10), Cat("Parent Meetings", "FAMILY", 6)) to listOf(
                Spec("Grade midterm essays — 9B", "Grading") { list("Emma — A" to true, "Liam — B+" to true, "Olivia — A-" to true, "Noah" to false, "Ava" to false, "Lucas" to false) },
                Spec("Field trip permission slips", "Class 9B") { list("Collected 18 / 24" to true, "Bus booked" to true, "Lunch list" to false) },
                Spec("Science fair — pick topics", "Class 9B") { copy(plannedDateEpochDay = it.day(4)) },
                Spec("Seating plan update", "Class 9B") { done() },
                Spec("Quiz: photosynthesis", "Class 11A") { timed(1, 10) },
                Spec("Lab report feedback", "Class 11A") { list("Group 1" to true, "Group 2" to true, "Group 3" to false, "Group 4" to false) },
                Spec("Print revision sheets", "Class 11A") { done(30) },
                Spec("Report cards — final check", "Grading") { copy(plannedDateEpochDay = it.day(6)) },
                Spec("Upload attendance", "Grading") { done(5) },
                Spec("Meeting: Jacob's parents", "Parent Meetings") { timed(2, 15, 30).place("Room 204") },
                Spec("Call Mia's mom about reading", "Parent Meetings") { timed(0, 17) },
                Spec("Staff meeting", null) { timed(1, 8, 15) }
            )
            "student" -> listOf(Cat("Thesis Project", "IDEA", 10), Cat("Homework", "SCHOOL", 2), Cat("Exams", "FILE", 3), Cat("Group Projects", "FAMILY", 8)) to listOf(
                Spec("Literature review — 12 sources", "Thesis Project") { list("Find 12 papers" to true, "Summaries" to true, "Comparison table" to false, "Write section 2.1" to false) },
                Spec("Chapter 2 draft due", "Thesis Project") { copy(plannedDateEpochDay = it.day(3)) },
                Spec("Meet supervisor", "Thesis Project") { timed(2, 14) },
                Spec("Research proposal", "Thesis Project") { done(48) },
                Spec("Statistics — problem set 4", "Homework") { copy(plannedDateEpochDay = it.day(1)) },
                Spec("Essay: The Great Gatsby", "Homework") { list("Outline" to true, "Draft" to false, "Citations" to false) },
                Spec("Spanish vocab unit 6", "Homework") { done(10) },
                Spec("Calculus midterm", "Exams") { timed(5, 9).place("Hall B") },
                Spec("Biology flashcards", "Exams") { list("Cells" to true, "Genetics" to true, "Ecology" to false) },
                Spec("Marketing pitch deck", "Group Projects") { list("Slides 1–5 (me)" to true, "Slides 6–10 (Maya)" to false, "Rehearsal" to false) },
                Spec("Team sync with Maya & Leo", "Group Projects") { timed(1, 18) },
                Spec("Gym before class", null) { timed(1, 7).copy(repeatRule = RecurrenceRule.WEEKLY) }
            )
            "friends" -> listOf(Cat("Trips", "TRAVEL", 6), Cat("Meetups", "HEART", 12), Cat("Birthdays", "BIRTHDAY", 10), Cat("Watch Party", "MOVIE", 2)) to listOf(
                Spec("Lisbon weekend", "Trips") {
                    copy(plannedDateEpochDay = it.day(9)).list("Flights" to true, "Airbnb in Alfama" to true, "Tram 28 tickets" to false, "Pastéis de nata tour" to false)
                        .copy(location = ReminderLocation(placeName = "Lisbon", address = "Portugal", latitude = 38.7223, longitude = -9.1393))
                },
                Spec("Camping gear check", "Trips") { list("Tent" to true, "Sleeping bags" to false, "Stove" to false) },
                Spec("Book Tokyo hostel", "Trips") { done(26) },
                Spec("Coffee with Sara", "Meetups") { timed(1, 11).place("Blue Bottle Coffee", "Downtown") },
                Spec("Board game night", "Meetups") { timed(3, 20).place("Leo's place") },
                Spec("Hiking trail for Sunday 🥾", "Meetups") { copy(sentBy = "Leo", plannedDateEpochDay = it.day(3)).place("Pine Ridge Trail", "Trailhead parking") },
                Spec("Mom's birthday dinner", "Birthdays") { timed(3, 19, 30).copy(accentStart = 10, accentEnd = 11, isImportant = true) },
                Spec("Gift for Maya", "Birthdays") { list("Vinyl record" to true, "Card" to false) },
                Spec("Movie night at mine — bring snacks!", "Watch Party") { copy(sentBy = "Sara", plannedDateEpochDay = it.day(2)) },
                Spec("Finale watch party", "Watch Party") { done(40) }
            )
            else -> listOf(Cat("Case Files", "FILE", 2), Cat("Court Dates", "BOOKMARK", 3), Cat("Clients", "FAMILY", 6), Cat("Billing", "MONEY", 10)) to listOf(
                Spec("Review discovery documents", "Case Files") { list("Witness statements" to true, "Bank records 2023" to true, "Emails Mar–Jun" to false, "Expert report" to false) },
                Spec("Draft settlement — Patel family", "Case Files") { copy(plannedDateEpochDay = it.day(2)) },
                Spec("File motion to dismiss", "Case Files") { done(30) },
                Spec("Precedent research: Doe v. Allen", "Case Files") { done(50) },
                Spec("Hearing: Miller v. Stone — Court 4", "Court Dates") { timed(1, 9, 30).copy(isImportant = true).place("County Courthouse", "120 Main St") },
                Spec("Deposition: Dr. Reyes", "Court Dates") { timed(4, 13).place("Office — Room 3") },
                Spec("Client call: Ana Torres", "Clients") { timed(0, 16) },
                Spec("Onboard new client — Kim Ltd.", "Clients") { list("Engagement letter" to true, "Conflict check" to true, "ID verification" to false) },
                Spec("Send case update to the Patels", "Clients") { done(8) },
                Spec("Invoice October hours", "Billing") { copy(plannedDateEpochDay = it.day(5)) },
                Spec("Expense receipts", "Billing") { done(70) },
                Spec("Bar association dinner", null) { timed(5, 19) }
            )
        }

        SyncLocal.preferences(ctx, "big_bos_cagri_categories", MODE_PRIVATE).edit().clear().commit()
        val categories = CategoryRepository(ctx)
        cats.forEach { categories.add(it.name, it.icon, it.color) }
        val loaded = categories.load()
        val ids = loaded.associate { it.name to it.id }
        // Category descriptions (the small pill next to the card count).
        val blurbs = mapOf("Case Files" to "Active cases", "Court Dates" to "This month", "Clients" to "3 new", "Billing" to "Due Friday",
            "Class 9B" to "28 students", "Class 11A" to "Biology", "Grading" to "Midterms", "Parent Meetings" to "This week",
            "Thesis Project" to "Due Dec 12", "Homework" to "This week", "Exams" to "Finals soon", "Group Projects" to "With Maya & Leo",
            "Trips" to "Lisbon next", "Meetups" to "Weekend", "Birthdays" to "Don't forget", "Watch Party" to "Friday nights")
        SyncLocal.preferences(ctx, "real_category_descriptions", MODE_PRIVATE).edit().apply {
            clear(); loaded.forEach { cat -> blurbs[cat.name]?.let { putString("custom_${cat.id}_${cat.createdAt}", it) } }
        }.commit()
        SyncLocal.preferences(ctx, "real_ui", MODE_PRIVATE).edit()
            .putString("category_order_v1", cats.joinToString("|") { "custom:${ids.getValue(it.name)}" }).commit()

        // Everyday cards every persona has: catalog covers (music / series / game) and a plain one.
        val levitating = MusicBlock(title = "Levitating", artist = "Dua Lipa", album = "Future Nostalgia",
            artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/6c/11/d6/6c11d681-aa3a-d59e-4c2e-f77e181026ab/190295092665.jpg/100x100bb.jpg",
            provider = "iTunes", providerTrackId = "1538003843", providerUrl = "https://music.apple.com/us/album/levitating/1538003494?i=1538003843",
            previewUrl = "https://audio-ssl.itunes.apple.com/itunes-assets/AudioPreview211/v4/59/dc/4d/59dc4dda-93ff-8f1c-c536-f005f6ea6af5/mzaf_3066686759813252385.plus.aac.p.m4a")
        val theBear = MediaBlock(type = MediaType.TV, providerId = "54198", title = "The Bear", year = 2022, genre = "Drama, Comedy",
            description = "Carmy, a young fine-dining chef, comes home to Chicago to run his family sandwich shop.",
            artworkUrl = "https://static.tvmaze.com/uploads/images/original_untouched/629/1574642.jpg",
            provider = "TVmaze", providerUrl = "https://www.tvmaze.com/shows/54198/the-bear")
        val stardew = CardGameBlock(providerId = "17000", title = "Stardew Valley", year = 2016, genres = listOf("simulator", "rpg"),
            platforms = listOf("PC", "Switch", "PlayStation", "iOS"),
            artworkUrl = "https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/0e/df/12/0edf1230-3f6e-fbb3-7f72-f2aa844d100a/AppIcons-0-0-1x_U007emarketing-0-8-0-85-220.png/512x512bb.jpg",
            providerUrl = "https://www.igdb.com/games/stardew-valley")
        val hero = CardBlockPlacement(span = 6, minHeight = 360)
        val everyday = listOf(
            Spec("Road trip playlist", null) { copy(musicBlocks = listOf(levitating), contentBlockOrder = listOf("music:${levitating.id}", "text"),
                contentBlockLayout = mapOf("music:${levitating.id}" to hero)) },
            Spec("Watch The Bear — season 3", null) { copy(mediaBlocks = listOf(theBear), contentBlockOrder = listOf("media:${theBear.id}", "text"),
                contentBlockLayout = mapOf("media:${theBear.id}" to hero)) },
            Spec("Stardew co-op night", null) { copy(gameBlocks = listOf(stardew), plannedDateEpochDay = it.day(2),
                contentBlockOrder = listOf("game:${stardew.id}", "text"), contentBlockLayout = mapOf("game:${stardew.id}" to hero)) },
            Spec("Pick up dry cleaning", null)
        )

        // A busy goal history for the first category: goals finished on different days this week ("This week" bars).
        val goalTitles = when (persona) {
            "teacher" -> listOf("Return quizzes", "Update gradebook", "Plan unit 4", "Email reading list", "Prep lab kits", "Seating chart", "Homework check", "Book library slot")
            "student" -> listOf("Outline chapter 3", "Read 3 papers", "Survey draft", "Ethics form", "Cite sources", "Data cleaning", "Figures 1–4", "Abstract v2")
            "friends" -> listOf("Pack charger", "Exchange euros", "Travel insurance", "Museum tickets", "Airport transfer", "Restaurant list", "Check-in online", "Print boarding pass")
            else -> listOf("Witness list", "Exhibit binder", "Subpoena records", "Expert call", "Draft opening", "Jury notes", "Timeline chart", "File exhibits")
        }
        val goals = goalTitles.mapIndexed { i, t ->
            Spec(t, cats.first().name) { if (i < 6) copy(completedAt = now - (i % 5 + 1) * 86_400_000L / 1 - i * 3_600_000L) else this }
        }
        var nextId = now - 1_000_000
        val items = (specs + goals + everyday).mapIndexed { i, s ->
            val base = ReminderItem(id = nextId++, text = s.text, createdAt = now - (i + 1) * 3_600_000L * 5, sendImmediateNotification = false,
                customCategoryId = s.cat?.let { ids.getValue(it) })
            s.build(base, c)
        }
        check(SyncLocal.preferences(ctx, "big_bos_cagri_reminders", MODE_PRIVATE).edit().putString("reminders", ReminderJson.encode(items)).commit())
        items.forEach { Log.i("RealDemo", "card ${it.id} ${it.text}") }

        // Puanlarım: rated series (episode scores) for the signed-in demo account.
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            ctx.getSharedPreferences("real_media_ratings_$uid", MODE_PRIVATE).edit().clear().commit()
            val ratings = com.cagri.real.discovery.MediaRatingRepository(ctx, uid)
            fun show(id: String, title: String, year: Int, genre: String, art: String) = MediaBlock(type = MediaType.TV, providerId = id,
                title = title, year = year, genre = genre, artworkUrl = art, provider = "TVmaze", providerUrl = "https://www.tvmaze.com/shows/$id")
            val shows = listOf(
                show("54198", "The Bear", 2022, "Drama, Comedy", "https://static.tvmaze.com/uploads/images/original_untouched/629/1574642.jpg") to listOf(9.5, 9.0, 10.0, 8.5, 9.5),
                show("44933", "Severance", 2022, "Drama, Science-Fiction", "https://static.tvmaze.com/uploads/images/original_untouched/548/1371406.jpg") to listOf(9.0, 9.5, 8.5, 10.0),
                show("37336", "Shōgun", 2024, "Drama, Adventure", "https://static.tvmaze.com/uploads/images/original_untouched/506/1265637.jpg") to listOf(8.5, 9.0, 9.5),
                show("46562", "The Last of Us", 2023, "Drama, Action", "https://static.tvmaze.com/uploads/images/original_untouched/563/1409008.jpg") to listOf(10.0, 9.5, 7.5, 9.0),
                show("23470", "Succession", 2018, "Drama, Family", "https://static.tvmaze.com/uploads/images/original_untouched/453/1134275.jpg") to listOf(8.0, 8.5, 9.0),
                show("51394", "The White Lotus", 2021, "Drama, Comedy", "https://static.tvmaze.com/uploads/images/original_untouched/557/1393876.jpg") to listOf(7.5, 8.0, 8.5),
                show("2993", "Stranger Things", 2016, "Drama, Horror", "https://static.tvmaze.com/uploads/images/original_untouched/595/1489169.jpg") to listOf(8.5, 7.5, 9.0)
            )
            kotlinx.coroutines.runBlocking {
                shows.forEach { (media, scores) -> ratings.rateEpisodes(media, scores.mapIndexed { i, v -> "1:${i + 1}" to v }.toMap()) }
            }
        }

        // Water: 1.5 L of the 2 L goal today.
        SyncLocal.preferences(ctx, com.cagri.real.data.WaterTrackerRepository.PREFERENCES_NAME, MODE_PRIVATE).edit().clear().commit()
        val water = com.cagri.real.data.WaterTrackerRepository(SyncLocal.preferences(ctx, com.cagri.real.data.WaterTrackerRepository.PREFERENCES_NAME, MODE_PRIVATE))
        kotlinx.coroutines.runBlocking { listOf(250, 500, 250, 500).forEach { water.add(it) } }

        // Mood: a calm, good week.
        SyncLocal.preferences(ctx, "real_mood_tracker", MODE_PRIVATE).edit().clear().commit()
        val moods = MoodTrackerRepository(ctx)
        listOf(JournalMood.GOOD, JournalMood.CALM, JournalMood.VERY_GOOD, JournalMood.TIRED, JournalMood.HAPPY).forEach {
            moods.add(it, MoodDetails(scope = MoodScope.DAILY, descriptors = listOf("focused"), factors = listOf("work"), loggedAt = now))
        }
    }
}
