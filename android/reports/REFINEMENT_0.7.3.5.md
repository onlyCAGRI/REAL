# REAL 0.7.3.5 — Category / Voice / Date refinement

## Changed behavior

- Liquid category headers now group the color-filter and edit actions into one shared capsule with a center divider, matching the detail Now Bar + Star grouping. Glass keeps the existing separate actions.
- Liquid category color-selector mode no longer draws the large background capsule behind the color row.
- Voice parser consumes Turkish date locative suffixes such as `10 ocakta` / `11 ekimde` without leaving `ta/de/te/da` in reminder text.
- Explicit year phrases such as `2027 yılında`, `2027 yılı 'ta`, and `2027'de` are removed from reminder text and retained as a scheduling year hint.
- `yarın bu saatlerde ...` schedules tomorrow using the current local hour/minute and removes the full phrase from reminder text.
- Home reminder card dates omit the year (`26 May, 13:00`).
- Detail dates include weekday + year (`Çarşamba • 26 May 2027, 13:00`).

## Validation

- Parser sources compile standalone with the available Kotlin compiler together with `RecurrenceRule.kt`.
- Smoke cases verified: `10 ocakta`, `11 ekimde`, `2027 yılı 'ta`, `2027'de`, and `yarın bu saatlerde`.
- Date formatter smoke test verified compact Home and full Detail output.
- `git diff --check` equivalent with `core.whitespace=cr-at-eol` produced no whitespace diagnostics (exit status is 1 only because files differ under `--no-index`).
- Full `:app:compileOptimizedKotlin` could not start because this environment cannot resolve `services.gradle.org` to download Gradle 8.11.1. No Kotlin task was reached.
