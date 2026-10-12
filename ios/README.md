# REAL iOS

SwiftUI port of the Android app (`app/`). Scope of this first phase:

- **Kartlar**: create, edit, complete, star, swipe to *24 hour*, multi-select, delete (or delete a single
  occurrence of a repeating card). Detail screen with date/time, date-only planning, repeat
  (daily / weekly with weekday set / monthly / yearly, end date), duration, category, colour gradient
  (palette start/end, blend, dark text), checklist, journal + mood, card stack, photos, link, location.
- **Kategoriler**: built-in (24 hour, Planlı, Önemli, Uyarı yok, Tamamlananlar, Günlüklerim) with colour
  overrides, custom categories (name, description, icon, gradient, "only inside category"), progress,
  colour filter, sort, home deck order.
- **Takvim**: the *New* daily view only — greeting, Focus carousel (starred cards + category progress
  cards), snapping date ruler, all-day notes, compressed timeline with overlap lanes, quick add.

Not ported on purpose: Yaşam, Pano, RealPe, Classic/New+ calendar views. Cloud sync (Firebase / worker)
is not wired yet; data is stored locally in `Application Support/real-store.json` using the Android
field names, so the sync layer can be added on top.

The rules are ports of the Kotlin sources (`RecurrenceRule.kt`, `CategoryRules.kt`, `ReminderCategory.kt`,
`PlannerModels.kt`, `PlannerTimelineGeometry.kt`, `PlannerCalendarColors.kt`) and `RealTests` mirrors the
matching Android unit tests.

## Build

The Xcode project is generated from `project.yml`:

```bash
brew install xcodegen
cd ios && xcodegen generate && open Real.xcodeproj
```

CI (`.github/workflows/ios.yml`) builds on macOS, runs the tests, launches the app in the simulator with
`-REALDemo -REALScreen <home|category|builtin|detail|checklist|planner>` and uploads the screenshots and a
simulator build as artifacts.
