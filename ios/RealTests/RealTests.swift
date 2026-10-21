import XCTest
@testable import REAL

/// Ports of the Android unit tests that pin the card / category / planner rules.
final class RecurrenceTests: XCTestCase {
    let cal = Calendar.real

    func millis(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 9, _ min: Int = 0) -> EpochMillis {
        cal.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!.epochMillis
    }

    func testDailyNextOccurrence() {
        let base = millis(2026, 3, 10)
        XCTAssertEqual(Recurrence.nextOccurrence(base: base, rule: .daily, after: base), millis(2026, 3, 11))
    }

    func testMonthlyClampsToMonthEnd() {
        let base = millis(2026, 1, 31)
        XCTAssertEqual(Recurrence.nextOccurrence(base: base, rule: .monthly, after: base), millis(2026, 2, 28))
    }

    func testWeekdayMaskPicksNextMarkedDay() {
        // 2026-03-09 is a Monday.
        let base = millis(2026, 3, 9)
        let mask = Weekday.monday.bit | Weekday.wednesday.bit | Weekday.friday.bit
        XCTAssertEqual(Recurrence.nextRepeat(base: base, rule: .weekly, weekdaysMask: mask, untilEpochDay: nil, after: base),
                       millis(2026, 3, 11))
    }

    func testUntilDayEndsSeries() {
        let base = millis(2026, 3, 9)
        let until = cal.epochDay(of: millis(2026, 3, 10).date)
        XCTAssertNil(Recurrence.nextRepeat(base: base, rule: .daily, weekdaysMask: 0, untilEpochDay: until, after: millis(2026, 3, 10)))
    }

    func testSkippedDayIsHopped() {
        let base = millis(2026, 3, 9)
        let skipped = [cal.epochDay(of: millis(2026, 3, 10).date)]
        XCTAssertEqual(Recurrence.nextRepeat(base: base, rule: .daily, weekdaysMask: 0, untilEpochDay: nil, after: base, skipped: skipped),
                       millis(2026, 3, 11))
    }

    func testRepeatingToggleRollsForward() {
        var item = ReminderItem(id: 1, text: "x", createdAt: 0)
        item.alertAt = millis(2026, 3, 9)
        item.anchorAlertAt = item.alertAt
        item.repeatRule = .daily
        let toggled = item.toggledCompleted(now: millis(2026, 3, 9, 10))
        XCTAssertFalse(toggled.isCompleted)
        XCTAssertEqual(toggled.alertAt, millis(2026, 3, 10))
    }

    func testEpochDayRoundTrip() {
        let day: EpochDay = 20_000
        XCTAssertEqual(cal.epochDay(of: cal.startOfDay(epochDay: day)), day)
    }
}

final class CategoryRulesTests: XCTestCase {
    func testNoAlertExcludesCategorisedCards() {
        var r = ReminderItem(id: 1, text: "a", createdAt: 0)
        XCTAssertTrue(r.isNoAlert(now: 0))
        r.customCategoryId = 5
        XCTAssertFalse(r.isNoAlert(now: 0))
    }

    func test24HourWindow() {
        let now: EpochMillis = 1_000_000_000_000
        var r = ReminderItem(id: 1, text: "a", createdAt: 0)
        r.alertAt = now + 3_600_000
        XCTAssertTrue(r.isIn24HourWindow(now: now))
        r.alertAt = now + 2 * CategoryRules.rolling24HoursMillis
        XCTAssertFalse(r.isIn24HourWindow(now: now))
        r.quick24HourUntilAt = now + 10
        XCTAssertTrue(r.isIn24HourWindow(now: now))
    }

    func testProgressCountsCustomCategory() {
        var a = ReminderItem(id: 1, text: "a", createdAt: 0); a.customCategoryId = 9; a.completedAt = 5
        var b = ReminderItem(id: 2, text: "b", createdAt: 0); b.customCategoryId = 9
        let stats = [a, b].categoryProgress(builtIn: nil, customId: 9)
        XCTAssertEqual(stats?.done, 1)
        XCTAssertEqual(stats?.total, 2)
    }

    func testJsonIsTolerantOfMissingFields() throws {
        let json = #"{"id": 7, "text": "Merhaba", "createdAt": 1, "repeatRule": "WEEKLY"}"#.data(using: .utf8)!
        let r = try JSONDecoder().decode(ReminderItem.self, from: json)
        XCTAssertEqual(r.repeatRule, .weekly)
        XCTAssertTrue(r.sendImmediateNotification)
    }
}

final class PlannerTests: XCTestCase {
    func testLaneAllocation() {
        let g = PlannerTimelineGeometry(intervals: [
            PlannerTimeInterval(key: "a", startMinute: 60, endMinute: 120),
            PlannerTimeInterval(key: "b", startMinute: 90, endMinute: 150),
            PlannerTimeInterval(key: "c", startMinute: 130, endMinute: 160),
        ])
        let lanes = Dictionary(uniqueKeysWithValues: g.placements.map { ($0.interval.key, $0.lane) })
        XCTAssertEqual(lanes["a"], 0)
        XCTAssertEqual(lanes["b"], 1)
        XCTAssertEqual(lanes["c"], 0)
        XCTAssertEqual(g.laneCount, 2)
    }

    func testEmptyHoursCollapse() {
        let g = PlannerTimelineGeometry(intervals: [PlannerTimeInterval(key: "a", startMinute: 600, endMinute: 660)])
        XCTAssertEqual(g.hourHeights[10], 72)
        XCTAssertEqual(g.hourHeights[3], 12)
        XCTAssertEqual(g.y(630), g.boundaries[10] + 36, accuracy: 0.001)
    }

    func testRepeatingEventsExpandInRange() {
        let cal = Calendar.real
        let start = cal.date(from: DateComponents(year: 2026, month: 5, day: 4))!
        var r = ReminderItem(id: 1, text: "x", createdAt: 0)
        r.alertAt = cal.date(bySettingHour: 8, minute: 0, second: 0, of: start)!.epochMillis
        r.anchorAlertAt = r.alertAt
        r.repeatRule = .daily
        let end = cal.date(byAdding: .day, value: 7, to: start)!
        let events = Planner.events([r], rangeStart: start.epochMillis, rangeEnd: end.epochMillis)
        XCTAssertEqual(events.count, 7)
    }

    func testFirstName() {
        XCTAssertEqual(Planner.firstName("Çağrı Gül"), "Çağrı")
        XCTAssertEqual(Planner.firstName("Ali Veli Can"), "Ali Veli")
        XCTAssertNil(Planner.firstName("  "))
    }

    func testFocusHeading() {
        let d = Date()
        XCTAssertEqual(Planner.focusHeading(day: d, today: d, count: 2), "Today’s\nFocus (2)")
    }
}
