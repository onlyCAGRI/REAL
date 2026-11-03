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


final class ComposerPeopleTests: XCTestCase {
    private let ayse = ComposerKnownPerson(name: "Ayşe Yılmaz", uid: "uid-ayse", pictureVersion: 7)

    func testWithFormLeavesTheSentence() {
        let all = composerPersonSuggestions("silo izleyeceğim annemle")
        XCTAssertEqual(all.count, 1)
        let found = all[0]
        XCTAssertEqual(found.person.name, "Annem")
        XCTAssertEqual(found.text, "silo izleyeceğim")
        XCTAssertTrue(found.isNew)
    }

    func testSeparateIleAndBirlikteLeaveWithTheName() {
        let all = composerPersonSuggestions("babam ile birlikte kahve içicem")
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all[0].text, "kahve içicem")
    }

    func testOtherFormsStayInTheSentence() {
        let text = "anneme hediye al"
        let all = composerPersonSuggestions(text)
        XCTAssertEqual(all.count, 1)
        let found = all[0]
        XCTAssertEqual(found.person.name, "Annem")
        XCTAssertEqual(found.text, text)
    }

    func testNamesAreFoundWithOrWithoutCapitalsAndEndings() {
        XCTAssertEqual(composerPersonSuggestions("fatmayla yürüyüş").first?.person.name, "Fatma")
        XCTAssertEqual(composerPersonSuggestions("çağrıya yaz").first?.person.name, "Çağrı")
        XCTAssertEqual(composerPersonSuggestions("Mehmet'le maç").first?.person.name, "Mehmet")
    }

    func testEverydayWordsNeedACapital() {
        XCTAssertTrue(composerPersonSuggestions("yarın deniz kenarına gidicem").isEmpty)
        XCTAssertEqual(composerPersonSuggestions("Deniz'e yaz").first?.person.name, "Deniz")
    }

    func testConnectionWinsOverNameListAndBringsItsPicture() {
        let all = composerPersonSuggestions("ayşeyle kahve", known: [ayse])
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all[0].person, CardPerson(name: "Ayşe Yılmaz", uid: "uid-ayse", pictureVersion: 7))
    }

    func testNicknameFindsThePersonShowsTheNicknameAndKeepsThePicture() {
        let nese = ComposerKnownPerson(name: "Neşe Gül", uid: "uid-nese", pictureVersion: 3, nickname: "annem")
        let all = composerPersonSuggestions("annemle kahve", known: [nese])
        XCTAssertEqual(all.count, 1)
        let found = all[0]
        XCTAssertEqual(found.person, CardPerson(name: "annem", uid: "uid-nese", pictureVersion: 3))
        XCTAssertFalse(found.isNew)
    }

    func testSomeoneNotOnRealIsFoundByNameWithoutAPicture() {
        let all = composerPersonSuggestions("neşeyle kahve", known: [ComposerKnownPerson(name: "Neşe Gül", nickname: "annem")])
        XCTAssertEqual(all.count, 1)
        let found = all[0]
        XCTAssertEqual(found.person, CardPerson(name: "Neşe Gül"))
        XCTAssertFalse(found.isNew)
    }

    func testUnknownNameOrKinshipWordIsNew() {
        XCTAssertEqual(composerPersonSuggestions("annemle kahve").first?.isNew, true)
        XCTAssertEqual(composerPersonSuggestions("fatmayla kahve").first?.isNew, true)
    }

    func testPeopleAlreadyOnTheCardAreLeftOut() {
        XCTAssertTrue(composerPersonSuggestions("annemin evi", chosen: [CardPerson(name: "Annem")]).isEmpty)
    }

    func testLinksAndSlashSearchesAreNotReadAsPeople() {
        XCTAssertTrue(composerPersonSuggestions("/ayşe https://site.com/ali").isEmpty)
    }

    func testEmojiDoesNotBreakRanges() {
        let all = composerPersonSuggestions("🎬👩‍❤️‍💋‍👨 silo izleyeceğim annemle 🍿")
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all[0].person.name, "Annem")
        XCTAssertEqual(all[0].text, "🎬👩‍❤️‍💋‍👨 silo izleyeceğim 🍿")
    }

    func testAddTypedDoesNotDuplicateByNameOrNickname() {
        let suite = "real_contacts_test_\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = RealContacts(defaults: defaults)

        store.addTyped("İsmail")
        store.addTyped("ismail")
        XCTAssertEqual(store.list.count, 1)

        store.setNickname(uid: "uid-nese", name: "Neşe Gül", nickname: "annem")
        store.addTyped("Annem")
        XCTAssertEqual(store.list.count, 2)
    }

    func testBlankNicknameRemovesEntry() {
        let suite = "real_contacts_test_\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = RealContacts(defaults: defaults)

        store.setNickname(uid: "uid-nese", name: "Neşe Gül", nickname: "annem")
        XCTAssertEqual(store.list.count, 1)
        XCTAssertEqual(store.list[0].id, "uid-nese")

        store.setNickname(uid: "uid-nese", name: "Neşe Gül", nickname: "   ")
        XCTAssertTrue(store.list.isEmpty)
    }

    func testRealContactsIntegrationWithComposerSuggestions() {
        let suite = "real_contacts_test_\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = RealContacts(defaults: defaults)

        store.setNickname(uid: "uid-nese", name: "Neşe Gül", nickname: "annem")
        store.saveLocal(id: nil, name: "Selim Kaya", nickname: "")

        let known = [
            ComposerKnownPerson(name: "Neşe Gül", uid: "uid-nese", pictureVersion: 3,
                                nickname: store.list.first { $0.uid == "uid-nese" }?.nickname)
        ] + store.list.filter { $0.uid == nil }.map {
            ComposerKnownPerson(name: $0.name, uid: nil, pictureVersion: nil, nickname: $0.nickname)
        }

        let byNick = composerPersonSuggestions("annemle kahve", known: known)
        XCTAssertEqual(byNick.count, 1)
        XCTAssertEqual(byNick[0].person, CardPerson(name: "annem", uid: "uid-nese", pictureVersion: 3))
        XCTAssertFalse(byNick[0].isNew)

        let byLocalName = composerPersonSuggestions("selimle kahve", known: known)
        XCTAssertEqual(byLocalName.count, 1)
        XCTAssertEqual(byLocalName[0].person, CardPerson(name: "Selim Kaya", uid: nil, pictureVersion: nil))
        XCTAssertFalse(byLocalName[0].isNew)
    }

    func testConnectionForMatchesByUidNicknameTurkishCaseAndRejectsLocalOrUnknown() {
        let known = [
            ComposerKnownPerson(name: "Neşe Gül", uid: "uid-nese", pictureVersion: 3, nickname: "İsmail"),
            ComposerKnownPerson(name: "Ayşe Yılmaz", uid: "uid-ayse", pictureVersion: 7, nickname: "ablacım"),
            ComposerKnownPerson(name: "Selim Kaya", uid: nil, pictureVersion: nil, nickname: "kardeşim")
        ]

        // Match by uid (even if the card's display name differs)
        let byUid = connectionFor(person: CardPerson(name: "Annem", uid: "uid-nese"), known: known)
        XCTAssertEqual(byUid?.uid, "uid-nese")
        XCTAssertEqual(byUid?.pictureVersion, 3)

        // Match by nickname with Turkish casing ("ismail" -> "İsmail")
        let byNick = connectionFor(person: CardPerson(name: "  ismail  "), known: known)
        XCTAssertEqual(byNick?.uid, "uid-nese")

        // A name that is not in the list returns nil
        XCTAssertNil(connectionFor(person: CardPerson(name: "Bilinmeyen Kişi"), known: known))

        // An entry without a uid never matches (neither by name nor by nickname)
        XCTAssertNil(connectionFor(person: CardPerson(name: "Selim Kaya"), known: known))
        XCTAssertNil(connectionFor(person: CardPerson(name: "kardeşim"), known: known))
        XCTAssertNil(connectionFor(person: CardPerson(name: "Selim Kaya", uid: nil), known: known))
    }

    func testTurkishTitleAfterANameIsOnePerson() {
        let all = composerPersonSuggestions("Ayşe teyzeyle kahve")
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all[0].person.name, "Ayşe teyze")
        XCTAssertEqual(all[0].text, "kahve")
        XCTAssertTrue(all[0].isNew)

        XCTAssertEqual(composerPersonSuggestions("Ayşe teyze ile kahve").first?.text, "kahve")

        let gift = composerPersonSuggestions("Ayşe teyzeye hediye al")
        XCTAssertEqual(gift.count, 1)
        XCTAssertEqual(gift[0].person.name, "Ayşe teyze")
        XCTAssertEqual(gift[0].text, "Ayşe teyzeye hediye al")

        let poss = composerPersonSuggestions("ayşe teyzemle kahve")
        XCTAssertEqual(poss.count, 1)
        XCTAssertEqual(poss[0].person.name, "Ayşe teyze")
        XCTAssertEqual(poss[0].text, "kahve")

        XCTAssertEqual(composerPersonSuggestions("can abiyle kahve").first?.person.name, "Can abi")
        XCTAssertEqual(composerPersonSuggestions("Nihan teyzeyle kahve").first?.person.name, "Nihan teyze")
        XCTAssertTrue(composerPersonSuggestions("nihan teyzeyle").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("Yarın abiyle kahve").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("abi naber").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("teyzeyle").isEmpty)
    }

    func testHalaTrapAndKnownPeopleWithTurkishTitles() {
        let ali = composerPersonSuggestions("Ali hala gelmedi")
        XCTAssertEqual(ali.count, 1)
        XCTAssertEqual(ali[0].person.name, "Ali")
        XCTAssertEqual(ali[0].text, "Ali hala gelmedi")
        XCTAssertEqual(composerPersonSuggestions("Ayşe halayla").first?.person.name, "Ayşe hala")

        let vsYilmaz = composerPersonSuggestions("Ayşe teyzeyle", known: [ayse])
        XCTAssertEqual(vsYilmaz.count, 1)
        XCTAssertEqual(vsYilmaz[0].person.name, "Ayşe teyze")
        XCTAssertTrue(vsYilmaz[0].isNew)

        let knownTeyze = ComposerKnownPerson(name: "Fatma Kaya", uid: "uid-teyze", pictureVersion: 4, nickname: "Ayşe teyze")
        let matchedNick = composerPersonSuggestions("Ayşe teyzeyle", known: [knownTeyze])
        XCTAssertEqual(matchedNick.count, 1)
        XCTAssertEqual(matchedNick[0].person, CardPerson(name: "Ayşe teyze", uid: "uid-teyze", pictureVersion: 4))
        XCTAssertFalse(matchedNick[0].isNew)

        let byFirst = composerPersonSuggestions("Ayşe ile", known: [ComposerKnownPerson(name: "Ayşe teyze")])
        XCTAssertEqual(byFirst.count, 1)
        XCTAssertEqual(byFirst[0].person.name, "Ayşe teyze")
        XCTAssertFalse(byFirst[0].isNew)
    }

    func testEnglishKinTitlesPossessivesAndWordNames() {
        let momWith = composerPersonSuggestions("coffee with my mom")
        XCTAssertEqual(momWith.count, 1)
        XCTAssertEqual(momWith[0].person.name, "Mom")
        XCTAssertEqual(momWith[0].text, "coffee")

        let callMom = composerPersonSuggestions("call mom")
        XCTAssertEqual(callMom.count, 1)
        XCTAssertEqual(callMom[0].person.name, "Mom")
        XCTAssertEqual(callMom[0].text, "call mom")

        let sister = composerPersonSuggestions("coffee with my sister")
        XCTAssertEqual(sister.count, 1)
        XCTAssertEqual(sister[0].person.name, "My sister")
        XCTAssertEqual(sister[0].text, "coffee")
        XCTAssertTrue(composerPersonSuggestions("call sister").isEmpty)

        XCTAssertTrue(composerPersonSuggestions("mum al").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("son film").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("dadı geldi").isEmpty)
        XCTAssertEqual(composerPersonSuggestions("coffee with mum").first?.person.name, "Mum")

        let aunt = composerPersonSuggestions("dinner with Aunt Sarah")
        XCTAssertEqual(aunt.count, 1)
        XCTAssertEqual(aunt[0].person.name, "Aunt Sarah")
        XCTAssertEqual(aunt[0].text, "dinner")

        let uncle = composerPersonSuggestions("call my uncle Bob")
        XCTAssertEqual(uncle.count, 1)
        XCTAssertEqual(uncle[0].person.name, "Uncle Bob")
        XCTAssertEqual(uncle[0].text, "call my uncle Bob")

        XCTAssertEqual(composerPersonSuggestions("meet Mr. Smith").first?.person.name, "Mr Smith")
        XCTAssertEqual(composerPersonSuggestions("I miss Sarah").first?.person.name, "Sarah")

        let bday = composerPersonSuggestions("Sarah's birthday")
        XCTAssertEqual(bday.count, 1)
        XCTAssertEqual(bday[0].person.name, "Sarah")
        XCTAssertEqual(bday[0].text, "Sarah's birthday")

        let sarahsMom = composerPersonSuggestions("coffee with Sarah's mom")
        XCTAssertTrue(sarahsMom.allSatisfy { $0.text == "coffee with Sarah's mom" })

        XCTAssertTrue(composerPersonSuggestions("Ben geldim").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("Kim geldi").isEmpty)
        let ben = composerPersonSuggestions("lunch with Ben")
        XCTAssertEqual(ben.count, 1)
        XCTAssertEqual(ben[0].person.name, "Ben")
        XCTAssertEqual(ben[0].text, "lunch")

        let benAbim = composerPersonSuggestions("ben abimle sinemaya gideceğim")
        XCTAssertEqual(benAbim.count, 1)
        XCTAssertEqual(benAbim[0].person.name, "Abim")
        XCTAssertEqual(benAbim[0].text, "ben sinemaya gideceğim")

        XCTAssertEqual(composerPersonSuggestions("Ben abimle gideceğim").map(\.person.name), ["Abim"])
        XCTAssertTrue(composerPersonSuggestions("kim abiyle gitti").isEmpty)

        let kahveAbim = composerPersonSuggestions("Kahve abimle içeceğim")
        XCTAssertEqual(kahveAbim.count, 1)
        XCTAssertEqual(kahveAbim[0].person.name, "Abim")
        XCTAssertEqual(kahveAbim[0].text, "Kahve içeceğim")

        XCTAssertEqual(composerPersonSuggestions("Annemle abimle yemek").map(\.person.name), ["Annem", "Abim"])
        XCTAssertEqual(composerPersonSuggestions("Annem ablamla konuştu").map(\.person.name), ["Annem", "Ablam"])
        XCTAssertEqual(composerPersonSuggestions("Ayşeyle abimle kahve").map(\.person.name), ["Ayşe", "Abim"])

        let aliAbim = composerPersonSuggestions("Ali abimle kahve")
        XCTAssertEqual(aliAbim.count, 1)
        XCTAssertEqual(aliAbim[0].person.name, "Ali abi")
        XCTAssertEqual(aliAbim[0].text, "kahve")

        XCTAssertEqual(composerPersonSuggestions("Sena teyzeyle kahve").first?.person.name, "Sena teyze")
        XCTAssertTrue(composerPersonSuggestions("Yaz abiye").isEmpty)
        XCTAssertTrue(composerPersonSuggestions("Cuma hocayla görüş").isEmpty)

        let knownAnnem = ComposerKnownPerson(name: "Neşe Gül", uid: "uid-nese", pictureVersion: 3, nickname: "annem")
        let annemAbiyle = composerPersonSuggestions("annem abiyle", known: [knownAnnem])
        XCTAssertEqual(annemAbiyle.count, 1)
        XCTAssertEqual(annemAbiyle[0].person, CardPerson(name: "annem", uid: "uid-nese", pictureVersion: 3))
        XCTAssertFalse(annemAbiyle[0].isNew)

        XCTAssertEqual(composerPersonSuggestions("grandma will call").map(\.person.name), ["Grandma"])
        XCTAssertEqual(composerPersonSuggestions("Grandma Will called").map(\.person.name), ["Grandma Will"])

        let knownMom = ComposerKnownPerson(name: "Jane Doe", uid: "uid-mom", pictureVersion: 2, nickname: "Mom")
        let matchedMom = composerPersonSuggestions("call mom", known: [knownMom])
        XCTAssertEqual(matchedMom.count, 1)
        XCTAssertEqual(matchedMom[0].person.uid, "uid-mom")
        XCTAssertFalse(matchedMom[0].isNew)

        let knownSister = ComposerKnownPerson(name: "Emily Doe", uid: "uid-sis", pictureVersion: 5, nickname: "My sister")
        let matchedSister = composerPersonSuggestions("call my sister", known: [knownSister])
        XCTAssertEqual(matchedSister.count, 1)
        XCTAssertEqual(matchedSister[0].person.uid, "uid-sis")
        XCTAssertFalse(matchedSister[0].isNew)

        XCTAssertTrue(composerPersonSuggestions("Eve gittim. Kim geldi?").isEmpty)
        XCTAssertEqual(composerPersonSuggestions("Call Will").first?.person.name, "Will")
    }
}

final class ComposerSlashSearchTests: XCTestCase {
    func testTheQueryRunsFromTheSlashToTheEnd() {
        XCTAssertEqual(composerSlashQuery("yarın 9 da /silo")?.query, "silo")
        XCTAssertEqual(composerSlashQuery("yarın /")?.query, "")
    }

    func testASlashInsideAWordOrLinkIsNoSearch() {
        XCTAssertNil(composerSlashQuery("site.com/silo"))
        XCTAssertNil(composerSlashQuery("1/2 bardak"))
        XCTAssertNil(composerSlashQuery("silo"))
        XCTAssertNil(composerSlashQuery("https://site.com/a"))
    }

    func testPickingReplacesOnlyTheSearch() {
        XCTAssertEqual(replaceComposerSlashQuery("yarın 9 da /silo", "Silo"), "yarın 9 da Silo ")
    }

    func testSavingUnpickedLeavesTheWordsWithoutTheMark() {
        XCTAssertEqual(stripComposerSlash("/silo izleyeceğim"), "silo izleyeceğim")
        XCTAssertEqual(stripComposerSlash("yarın /"), "yarın")
        XCTAssertEqual(stripComposerSlash("https://site.com/silo"), "https://site.com/silo")
        XCTAssertEqual(stripComposerSlash("https://site.com/a"), "https://site.com/a")
    }
}

final class TypedDateParserTests: XCTestCase {
    private var previousLanguage: String?

    private var istanbulCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Istanbul")!
        cal.firstWeekday = 2
        cal.locale = Locale(identifier: "tr_TR")
        return cal
    }

    // 2026-09-18 14:00:00 Europe/Istanbul (Friday)
    private var baseFridayNow: Date {
        zonedDate(2026, 9, 18, 14, 0, 0, calendar: istanbulCalendar)
    }

    override func setUp() {
        super.setUp()
        previousLanguage = UserDefaults.standard.string(forKey: I18n.defaultsKey)
        UserDefaults.standard.set("tr", forKey: I18n.defaultsKey)
    }

    override func tearDown() {
        if let prev = previousLanguage {
            UserDefaults.standard.set(prev, forKey: I18n.defaultsKey)
        } else {
            UserDefaults.standard.removeObject(forKey: I18n.defaultsKey)
        }
        super.tearDown()
    }

    private func zonedDate(
        _ year: Int,
        _ month: Int,
        _ day: Int,
        _ hour: Int = 0,
        _ minute: Int = 0,
        _ second: Int = 0,
        calendar: Calendar
    ) -> Date {
        calendar.date(
            from: DateComponents(
                year: year,
                month: month,
                day: day,
                hour: hour,
                minute: minute,
                second: second
            )
        )!
    }

    private func localDay(_ year: Int, _ month: Int, _ day: Int, calendar: Calendar) -> Date {
        calendar.startOfDay(for: zonedDate(year, month, day, calendar: calendar))
    }

    // MARK: - Ported VoiceReminderParserTest cases (34 tests)

    func testBareHourHeuristicDirectTesting() {
        XCTAssertEqual(interpretHour(1), 13)
        XCTAssertEqual(interpretHour(5), 17)
        XCTAssertEqual(interpretHour(7), 19)

        XCTAssertEqual(interpretHour(8), 8)
        XCTAssertEqual(interpretHour(9), 9)
        XCTAssertEqual(interpretHour(11), 11)

        XCTAssertEqual(interpretHour(12), 12)

        XCTAssertEqual(interpretHour(17), 17)
        XCTAssertEqual(interpretHour(22), 22)

        XCTAssertEqual(interpretHour(5, .morning), 5)
        XCTAssertEqual(interpretHour(9, .morning), 9)
        XCTAssertEqual(interpretHour(1, .noon), 13)
        XCTAssertEqual(interpretHour(5, .evening), 17)
        XCTAssertEqual(interpretHour(8, .evening), 20)
        XCTAssertEqual(interpretHour(11, .night), 23)
        XCTAssertEqual(interpretHour(1, .night), 1)
    }

    func testTomorrowBareHourFive() {
        let input = "yarın saat 5te annemi ara"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 17, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowBareHourFiveWithColloquialVerb() {
        let input = "yarın saat 5'te annemi arıcam"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 17, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowExplicitTimeSeventeenThirty() {
        let input = "yarın 17:30'da annemi ara"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 17, 30, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTodayMorningEight() {
        let morningNow = zonedDate(2026, 9, 18, 7, 0, 0, calendar: istanbulCalendar)
        let input = "bugün saat 8'de markete git"
        let parsed = TypedDateParser.parse(input, now: morningNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Markete git")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 8, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTodayEveningEight() {
        let input = "bugün akşam 8'de markete git"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Markete git")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 20, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowMorningFive() {
        let input = "yarın sabah 5te koşuya çık"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Koşuya çık")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 5, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowMorningNine() {
        let input = "yarın sabah 9'da doktoru ara"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Doktoru ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 9, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowNoonOne() {
        let input = "yarın öğlen 1'de yemek ye"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Yemek ye")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 13, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowEveningSeven() {
        let input = "yarın akşam 7'de eve git"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Eve git")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 19, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowEveningEight() {
        let input = "yarın akşam 8de markete git"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Markete git")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 20, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testTomorrowNightEleven() {
        let input = "yarın gece 11'de ilacı al"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "İlacı al")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 23, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testRelativeTenMinutesLater() {
        let input = "10 dakika sonra çamaşırı çıkar"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Çamaşırı çıkar")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 14, 10, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testRelativeHalfHourLater() {
        let input = "yarım saat sonra fırını kapat"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Fırını kapat")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 14, 30, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testRelativeTwoHoursLater() {
        let input = "2 saat sonra toplantıya gir"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Toplantıya gir")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 16, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testWeekdayUpcomingMondayTen() {
        let input = "pazartesi saat 10'da raporu gönder"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Raporu gönder")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 21, 10, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testWeekdayFridaySameDayFutureTime() {
        let input = "cuma akşam 6'da annemi ara"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 18, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testWeekdayFridaySameDayPastTimeAdvancesToNextWeek() {
        let lateFridayNow = zonedDate(2026, 9, 18, 19, 0, 0, calendar: istanbulCalendar)
        let input = "cuma akşam 6da annemi ara"
        let parsed = TypedDateParser.parse(input, now: lateFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 25, 18, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
        XCTAssertTrue(parsed.alertAt! > lateFridayNow)
    }

    func testSpecificDateWithTurkishLocativeSuffixIsRemovedCleanly() {
        let january = TypedDateParser.parse("10 ocakta randevum var", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(january.text, "Randevum var")
        XCTAssertEqual(january.dateHint, localDay(2027, 1, 10, calendar: istanbulCalendar))
        XCTAssertNil(january.alertAt)
        XCTAssertTrue(january.requiresTimeSelection)

        let october = TypedDateParser.parse("11 ekimde randevum var", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(october.text, "Randevum var")
        XCTAssertEqual(october.dateHint, localDay(2026, 10, 11, calendar: istanbulCalendar))
        XCTAssertNil(october.alertAt)
        XCTAssertTrue(october.requiresTimeSelection)
    }

    func testExplicitYearPhraseIsSchedulingMetadataNotReminderText() {
        let parsed = TypedDateParser.parse("2027 yılında hastaneye git", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Hastaneye git")
        XCTAssertEqual(parsed.dateHint, localDay(2027, 9, 18, calendar: istanbulCalendar))
        XCTAssertNil(parsed.alertAt)
        XCTAssertTrue(parsed.requiresTimeSelection)

        let splitSuffix = TypedDateParser.parse("2027 yılı 'ta hastaneye git", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(splitSuffix.text, "Hastaneye git")
        XCTAssertEqual(splitSuffix.dateHint, localDay(2027, 9, 18, calendar: istanbulCalendar))
    }

    func testTomorrowThisTimeUsesCurrentLocalHourAndMinute() {
        let now = zonedDate(2026, 9, 18, 15, 20, 43, calendar: istanbulCalendar)
        let parsed = TypedDateParser.parse("yarın bu saatlerde Ahmet'i ara", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Ahmet'i ara")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 15, 20, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
        XCTAssertFalse(parsed.requiresTimeSelection)
    }

    func testSpecificDateSeptemberTwentyThreeFourteen() {
        let input = "23 eylül saat 14'te toplantıya git"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Toplantıya git")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 23, 14, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testSpecificDateSeptemberTwentyThreeFourteenThirty() {
        let input = "23 eylül 14:30'da toplantıya git"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Toplantıya git")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 23, 14, 30, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testSpecificDateRollsOverToNextYearIfPassed() {
        let input = "15 mayıs saat 10'da vize başvurusu yap"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Vize başvurusu yap")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2027, 5, 15, 10, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
        XCTAssertTrue(parsed.alertAt! > baseFridayNow)
    }

    func testMonthEndRollover() {
        let endOfMonth = zonedDate(2026, 3, 31, 10, 0, 0, calendar: istanbulCalendar)
        let input = "yarın saat 11'de fatura öde"
        let parsed = TypedDateParser.parse(input, now: endOfMonth, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Fatura öde")

        let expected = zonedDate(2026, 4, 1, 11, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testNoDateTimeExpressionPreservesTextWithoutAlert() {
        let input = "annemi ara"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertNil(parsed.alertAt)
    }

    func testVariousApostropheAndSpaceFormats() {
        let formats = [
            "yarın saat 5'te annemi ara",
            "yarın saat 5te annemi ara",
            "yarın saat 5 te annemi ara",
            "yarın saat 5 de annemi ara",
            "yarın saat 5'de annemi ara"
        ]
        let expected = zonedDate(2026, 9, 19, 17, 0, 0, calendar: istanbulCalendar)
        for f in formats {
            let parsed = TypedDateParser.parse(f, now: baseFridayNow, calendar: istanbulCalendar)
            XCTAssertEqual(parsed.text, "Annemi ara")
            XCTAssertEqual(parsed.alertAt, expected)
        }
    }

    func testDifferentTimezoneNewYork() {
        var nyCal = Calendar(identifier: .gregorian)
        nyCal.timeZone = TimeZone(identifier: "America/New_York")!
        nyCal.firstWeekday = 2
        nyCal.locale = Locale(identifier: "tr_TR")

        let nyNow = zonedDate(2026, 9, 18, 14, 0, 0, calendar: nyCal)
        let input = "yarın saat 5te annemi ara"
        let parsed = TypedDateParser.parse(input, now: nyNow, calendar: nyCal)
        XCTAssertEqual(parsed.text, "Annemi ara")

        let expected = zonedDate(2026, 9, 19, 17, 0, 0, calendar: nyCal)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testVerbNormalizations() {
        XCTAssertEqual(normalizeVerb("arıcam"), "ara")
        XCTAssertEqual(normalizeVerb("arayacağım"), "ara")
        XCTAssertEqual(normalizeVerb("kapatıcam"), "kapat")
        XCTAssertEqual(normalizeVerb("alıcam"), "al")
        XCTAssertEqual(normalizeVerb("gidicem"), "git")
        XCTAssertEqual(normalizeVerb("gideceğim"), "git")
        XCTAssertEqual(normalizeVerb("yiyeceğim"), "ye")

        XCTAssertEqual(normalizeVerb("ara"), "ara")
        XCTAssertEqual(normalizeVerb("kitap"), "kitap")
    }

    func testDailyRecurrenceWithNormalizingVerb() {
        let input = "akşam saat 7 ilaçlarımı alıcam her gün"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "İlaçlarımı al")
        XCTAssertEqual(parsed.repeatRule, .daily)
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 19, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testWeeklyRecurrenceWithWeekdayAndDaypartBareDigit() {
        let input = "cuma akşam 6 annemi ara her hafta"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertEqual(parsed.repeatRule, .weekly)
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 18, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testWeeklyRecurrenceWithSpecificWeekdayPrefix() {
        let input = "her pazartesi saat 9 toplantıya gir"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Toplantıya gir")
        XCTAssertEqual(parsed.repeatRule, .weekly)
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 21, 9, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testMonthlyRecurrenceWithDayOfMonthNoHour() {
        let input = "her ayın 5inde faturayı öde"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Faturayı öde")
        XCTAssertEqual(parsed.repeatRule, .monthly)
        XCTAssertNil(parsed.alertAt)
        XCTAssertTrue(parsed.requiresTimeSelection)
        XCTAssertEqual(parsed.dateHint, localDay(2026, 10, 5, calendar: istanbulCalendar))
    }

    func testEveningBareWordHourWithoutSaatOrSuffix() {
        let input = "akşam yedi ilaç al"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "İlaç al")
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 19, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testEveningRecurrenceWithTrailingHatirlatAndColloquialVerb() {
        let input = "Her akşam saat 8 de ilaçlarımı alacağım hatırlat"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "İlaçlarımı al")
        XCTAssertEqual(parsed.repeatRule, .daily)
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 18, 20, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testMorningRecurrenceWithBanaHatirlat() {
        let input = "her sabah 8 yürüyüş yap bana hatırlat"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Yürüyüş yap")
        XCTAssertEqual(parsed.repeatRule, .daily)
        XCTAssertNotNil(parsed.alertAt)

        let expected = zonedDate(2026, 9, 19, 8, 0, 0, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.alertAt, expected)
    }

    func testHerCumaHaliSahaVarDoesNotInventNineAm() {
        let input = "Her cuma halı saha var"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Halı saha var")
        XCTAssertEqual(parsed.repeatRule, .weekly)
        XCTAssertNil(parsed.alertAt)
        XCTAssertTrue(parsed.requiresTimeSelection)
        XCTAssertEqual(parsed.dateHint.map { istanbulCalendar.isoWeekday(of: $0) }, .friday)
        XCTAssertEqual(
            TypedDateParser.formatPendingScheduleLabel(
                parsed.dateHint,
                parsed.repeatRule,
                parsed.timeOfDay,
                now: baseFridayNow,
                calendar: istanbulCalendar
            ),
            "Cuma · Saat seç"
        )
    }

    func testHerCumaSaatSekizdeHaliSahaVarPreservesExistingBehavior() {
        let input = "her cuma saat 8'de halı saha var"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Halı saha var")
        XCTAssertEqual(parsed.repeatRule, .weekly)
        XCTAssertNotNil(parsed.alertAt)
        XCTAssertFalse(parsed.requiresTimeSelection)
    }

    func testYarinDoktoraGitNoExactHour() {
        let input = "Yarın doktora git"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Doktora git")
        XCTAssertEqual(parsed.repeatRule, .none)
        XCTAssertNil(parsed.alertAt)
        XCTAssertTrue(parsed.requiresTimeSelection)
        XCTAssertEqual(parsed.dateHint, localDay(2026, 9, 19, calendar: istanbulCalendar))
        XCTAssertEqual(
            TypedDateParser.formatPendingScheduleLabel(
                parsed.dateHint,
                parsed.repeatRule,
                parsed.timeOfDay,
                now: baseFridayNow,
                calendar: istanbulCalendar
            ),
            "Yarın · Saat seç"
        )
    }

    func testHerAksamIlacAlPreservesEveningDaypart() {
        let input = "Her akşam ilaç al"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "İlaç al")
        XCTAssertEqual(parsed.repeatRule, .daily)
        XCTAssertNil(parsed.alertAt)
        XCTAssertTrue(parsed.requiresTimeSelection)
        XCTAssertEqual(parsed.timeOfDay, .evening)
        XCTAssertEqual(
            TypedDateParser.formatPendingScheduleLabel(
                parsed.dateHint,
                parsed.repeatRule,
                parsed.timeOfDay,
                now: baseFridayNow,
                calendar: istanbulCalendar
            ),
            "Her akşam · Saat seç"
        )
    }

    func testHerHaftaToplantiNoWeekdayOrDateUydurma() {
        let input = "Her hafta toplantı"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Toplantı")
        XCTAssertEqual(parsed.repeatRule, .weekly)
        XCTAssertNil(parsed.alertAt)
        XCTAssertTrue(parsed.requiresTimeSelection)
        XCTAssertNil(parsed.dateHint)
        XCTAssertEqual(
            TypedDateParser.formatPendingScheduleLabel(
                parsed.dateHint,
                parsed.repeatRule,
                parsed.timeOfDay,
                now: baseFridayNow,
                calendar: istanbulCalendar
            ),
            "Her hafta · Tarih ve saat seç"
        )
    }

    func testAnnemiAraUnscheduledNoTimeRequired() {
        let input = "annemi ara"
        let parsed = TypedDateParser.parse(input, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(parsed.text, "Annemi ara")
        XCTAssertEqual(parsed.repeatRule, .none)
        XCTAssertNil(parsed.alertAt)
        XCTAssertFalse(parsed.requiresTimeSelection)
        XCTAssertNil(parsed.dateHint)
    }

    func testDailyRecurrenceVariantsMapToDaily() {
        let p1 = TypedDateParser.parse("günlük tekrar eden saat 3te su iç", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p1.text, "Su iç")
        XCTAssertEqual(p1.repeatRule, .daily)
        XCTAssertNotNil(p1.alertAt)

        let p2 = TypedDateParser.parse("günlük tekrar saat 3te su iç", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p2.text, "Su iç")
        XCTAssertEqual(p2.repeatRule, .daily)
        XCTAssertNotNil(p2.alertAt)

        let p3 = TypedDateParser.parse("her gün tekrar eden saat 3te su iç", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p3.text, "Su iç")
        XCTAssertEqual(p3.repeatRule, .daily)
        XCTAssertNotNil(p3.alertAt)

        let p4 = TypedDateParser.parse("her gün tekrarla saat 3te su iç", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p4.text, "Su iç")
        XCTAssertEqual(p4.repeatRule, .daily)
        XCTAssertNotNil(p4.alertAt)

        let p5 = TypedDateParser.parse("günlük tekrarlayan saat 3te su iç", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p5.text, "Su iç")
        XCTAssertEqual(p5.repeatRule, .daily)
        XCTAssertNotNil(p5.alertAt)
    }

    func testBareGunlukGuardedFromRecurrence() {
        let p1 = TypedDateParser.parse("günlük tut", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p1.text, "Günlük tut")
        XCTAssertEqual(p1.repeatRule, .none)
        XCTAssertNil(p1.alertAt)
        XCTAssertFalse(p1.requiresTimeSelection)

        let p2 = TypedDateParser.parse("günlük yaz", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p2.text, "Günlük yaz")
        XCTAssertEqual(p2.repeatRule, .none)

        let p3 = TypedDateParser.parse("günlük gazete al", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p3.text, "Günlük gazete al")
        XCTAssertEqual(p3.repeatRule, .none)
    }

    func testTurkishFillerYaStrippedAtBoundaries() {
        let p1 = TypedDateParser.parse("ya yarın saat 3 te toplantıyı hatırlat", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p1.text, "Toplantıyı")
        XCTAssertNotNil(p1.alertAt)

        let p2 = TypedDateParser.parse("yarın saat 3 te toplantıyı hatırlat ya", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p2.text, "Toplantıyı")
        XCTAssertNotNil(p2.alertAt)

        let p3 = TypedDateParser.parse("ya bana akşam ekmek almayı hatırlat ya", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p3.text, "Bana akşam ekmek almayı")

        let p3b = TypedDateParser.parse("ya yarın akşam ekmek al ya", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p3b.text, "Ekmek al")
        XCTAssertEqual(p3b.timeOfDay, .evening)

        let p4 = TypedDateParser.parse("YA, yarın saat 3 te toplantıyı hatırlat ya!", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p4.text, "Toplantıyı")
        XCTAssertNotNil(p4.alertAt)
    }

    func testTurkishWordsWithYaPreserved() {
        let p1 = TypedDateParser.parse("Yasin'i ara", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p1.text, "Yasin'i ara")

        let p2 = TypedDateParser.parse("yazıcıyı kontrol et", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p2.text, "Yazıcıyı kontrol et")

        let p3 = TypedDateParser.parse("yarın toplantı var", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p3.text, "Toplantı var")
        XCTAssertEqual(p3.dateHint, localDay(2026, 9, 19, calendar: istanbulCalendar))

        let p4 = TypedDateParser.parse("sinemaya git", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p4.text, "Sinemaya git")

        let p5 = TypedDateParser.parse("masaya koy", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p5.text, "Masaya koy")

        let p6 = TypedDateParser.parse("bankaya uğra", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p6.text, "Bankaya uğra")

        let p7 = TypedDateParser.parse("ya da markete git", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(p7.text, "Ya da markete git")
    }

    // MARK: - Word boundaries for Turkish letters & composerDateSuggestions tests

    func testTurkishWordBoundariesForYarinAksamOglen() {
        let yarin = TypedDateParser.parse("yarın öğlen toplantı", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(yarin.dateHint, localDay(2026, 9, 19, calendar: istanbulCalendar))
        XCTAssertEqual(yarin.timeOfDay, .noon)

        // Suffixed words like "yarınki", "akşamki", "öğlenci" must not match \b(yarın|akşam|öğlen)\b
        let yarinki = TypedDateParser.parse("yarınki maç", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertNil(yarinki.dateHint)
        XCTAssertNil(yarinki.alertAt)

        let aksamki = TypedDateParser.parse("akşamki film", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertNil(aksamki.timeOfDay)

        let oglenci = TypedDateParser.parse("öğlenci grup", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertNil(oglenci.timeOfDay)
    }

    func testBareHourGivesTwoChipsMorningFirst() {
        let chips = composerDateSuggestions("yarın 9'da spor", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 2)
        XCTAssertEqual(chips[0].label, "Yarın 09:00")
        XCTAssertEqual(chips[1].label, "Yarın 21:00")
        XCTAssertEqual(chips[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0, calendar: istanbulCalendar))
        XCTAssertEqual(chips[1].alertAt, zonedDate(2026, 9, 19, 21, 0, 0, calendar: istanbulCalendar))
        XCTAssertEqual(chips[0].text.lowercased(with: Locale(identifier: "tr_TR")), "spor")
    }

    func testWrittenLeadingZeroAndWritten24HourGiveOneChip() {
        let morning = composerDateSuggestions("yarın 09:00 spor", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(morning.count, 1)
        XCTAssertEqual(morning[0].label, "Yarın 09:00")
        XCTAssertEqual(morning[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0, calendar: istanbulCalendar))

        let evening = composerDateSuggestions("yarın 21:00 spor", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(evening.count, 1)
        XCTAssertEqual(evening[0].label, "Yarın 21:00")
        XCTAssertEqual(evening[0].alertAt, zonedDate(2026, 9, 19, 21, 0, 0, calendar: istanbulCalendar))
    }

    func testDayWithDaypartGivesOneChipWithDateHintAndTimeOfDayNoAlertAt() {
        let chips = composerDateSuggestions("yarın akşam spor", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 1)
        XCTAssertEqual(chips[0].label, "Yarın akşam")
        XCTAssertNil(chips[0].alertAt)
        XCTAssertEqual(chips[0].dateHint, localDay(2026, 9, 19, calendar: istanbulCalendar))
        XCTAssertEqual(chips[0].timeOfDay, .evening)
    }

    func testRelativeHoursLaterGivesOneChip() {
        let chips = composerDateSuggestions("2 saat sonra ara", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 1)
        XCTAssertEqual(chips[0].label, "Bugün 16:00")
        XCTAssertEqual(chips[0].alertAt, zonedDate(2026, 9, 18, 16, 0, 0, calendar: istanbulCalendar))
    }

    func testRecurringWeekdayAndTimeGivesWeeklyLabel() {
        let chips = composerDateSuggestions("her cuma 18:00 spor", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 1)
        XCTAssertEqual(chips[0].repeatRule, .weekly)
        XCTAssertEqual(chips[0].label, "Her Cuma 18:00")
    }

    func testEmojiBeforeDatePhraseDoesNotShiftRemoval() {
        let chips = composerDateSuggestions("🎬 yarın 9'da film", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 2)
        XCTAssertEqual(chips[0].text, "🎬 film")
        XCTAssertEqual(chips[1].text, "🎬 film")
    }

    func testPlainTextWithNoDateGivesNoChips() {
        XCTAssertTrue(composerDateSuggestions("annemi ara", now: baseFridayNow, calendar: istanbulCalendar).isEmpty)
        XCTAssertTrue(composerDateSuggestions("   ", now: baseFridayNow, calendar: istanbulCalendar).isEmpty)
    }

    func testCurrentAlertAtEqualToReadingLeavesThatReadingOut() {
        let nineAm = zonedDate(2026, 9, 19, 9, 0, 0, calendar: istanbulCalendar)
        let chips = composerDateSuggestions(
            "yarın 9'da spor",
            alertAt: nineAm,
            now: baseFridayNow,
            calendar: istanbulCalendar
        )
        XCTAssertEqual(chips.count, 1)
        XCTAssertEqual(chips[0].label, "Yarın 21:00")

        let tomorrow = localDay(2026, 9, 19, calendar: istanbulCalendar)
        let dayHintChips = composerDateSuggestions(
            "yarın akşam spor",
            dateHint: tomorrow,
            now: baseFridayNow,
            calendar: istanbulCalendar
        )
        XCTAssertTrue(dayHintChips.isEmpty)
    }

    func testCalendarRealInZoneDifferentFromSystemZoneUsesCalendarRealWallClock() {
        let originalDefaultTz = NSTimeZone.default
        defer { NSTimeZone.default = originalDefaultTz }

        let realZone = Calendar.real.timeZone
        // Pick a system timeZone guaranteed to differ from Calendar.real.timeZone.
        let otherZoneId = realZone.secondsFromGMT() == 0 ? "Pacific/Auckland" : "GMT"
        NSTimeZone.default = TimeZone(identifier: otherZoneId)!
        XCTAssertNotEqual(Calendar.current.timeZone.secondsFromGMT(), realZone.secondsFromGMT())

        let nowInReal = zonedDate(2026, 9, 18, 14, 0, 0, calendar: .real)
        let parsed = TypedDateParser.parse("yarın saat 5te annemi ara", now: nowInReal)
        let expectedInReal = zonedDate(2026, 9, 19, 17, 0, 0, calendar: .real)
        XCTAssertEqual(parsed.alertAt, expectedInReal)
        XCTAssertEqual(Calendar.real.component(.hour, from: parsed.alertAt!), 17)

        let chips = composerDateSuggestions("yarın 9'da spor", now: nowInReal)
        XCTAssertEqual(chips.count, 2)
        XCTAssertEqual(Calendar.real.component(.hour, from: chips[0].alertAt!), 9)
        XCTAssertEqual(Calendar.real.component(.hour, from: chips[1].alertAt!), 21)
    }

    func testStandInHatirlaticiChecklistAndPastSecondReading() {
        let onlyDate = composerDateSuggestions("yarın 9'da", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(onlyDate.count, 2)
        XCTAssertEqual(onlyDate[0].text, "")

        let explicitHatirlatici = composerDateSuggestions("yarın 9'da Hatırlatıcı", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(explicitHatirlatici[0].text, "Hatırlatıcı")

        let checklistText = "yarın 9'da alışveriş listesi: süt, ekmek ve yumurta"
        let checklistChips = composerDateSuggestions(checklistText, now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(checklistChips.count, 2)
        XCTAssertEqual(checklistChips[0].text, checklistText)

        // At 14:00 today, "bugün 5'te spor" -> primary is 17:00 today; other reading (05:00 today) is in the past and dropped when no repeat.
        let todayFive = composerDateSuggestions("bugün 5'te spor", now: baseFridayNow, calendar: istanbulCalendar)
        XCTAssertEqual(todayFive.count, 1)
        XCTAssertEqual(todayFive[0].label, "Bugün 17:00")
    }
}

final class EnglishDateParserTests: XCTestCase {
    private var previousLanguage: String?

    private var istanbulCalendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Istanbul")!
        cal.firstWeekday = 2
        cal.locale = Locale(identifier: "tr_TR")
        return cal
    }

    // Friday 2026-09-18 14:00:00 Europe/Istanbul
    private var now: Date {
        zonedDate(2026, 9, 18, 14, 0, 0)
    }

    override func setUp() {
        super.setUp()
        previousLanguage = UserDefaults.standard.string(forKey: I18n.defaultsKey)
        UserDefaults.standard.set("tr", forKey: I18n.defaultsKey)
    }

    override func tearDown() {
        if let prev = previousLanguage {
            UserDefaults.standard.set(prev, forKey: I18n.defaultsKey)
        } else {
            UserDefaults.standard.removeObject(forKey: I18n.defaultsKey)
        }
        super.tearDown()
    }

    private func zonedDate(
        _ year: Int,
        _ month: Int,
        _ day: Int,
        _ hour: Int = 0,
        _ minute: Int = 0,
        _ second: Int = 0
    ) -> Date {
        istanbulCalendar.date(
            from: DateComponents(
                year: year,
                month: month,
                day: day,
                hour: hour,
                minute: minute,
                second: second
            )
        )!
    }

    private func localDay(_ year: Int, _ month: Int, _ day: Int) -> Date {
        istanbulCalendar.startOfDay(for: zonedDate(year, month, day))
    }

    func testCallMomTomorrowAt9GivesTwoChipsMorningFirst() {
        let chips = composerDateSuggestions("call mom tomorrow at 9", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 2)
        XCTAssertEqual(chips[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0))
        XCTAssertEqual(chips[1].alertAt, zonedDate(2026, 9, 19, 21, 0, 0))
        XCTAssertEqual(chips[0].text, "Call mom")
    }

    func testAmAndPmGiveOneChip() {
        let pm = composerDateSuggestions("gym tomorrow at 5pm", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(pm.count, 1)
        XCTAssertEqual(pm[0].alertAt, zonedDate(2026, 9, 19, 17, 0, 0))
        XCTAssertEqual(pm[0].text, "Gym")

        let am = composerDateSuggestions("gym at 9am tomorrow", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(am.count, 1)
        XCTAssertEqual(am[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0))
        XCTAssertEqual(am[0].text, "Gym")
    }

    func testWritten24hAndWrittenLeadingZeroGiveOneChip() {
        let c1730 = composerDateSuggestions("gym tomorrow at 17:30", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(c1730.count, 1)
        XCTAssertEqual(c1730[0].alertAt, zonedDate(2026, 9, 19, 17, 30, 0))

        let c0900 = composerDateSuggestions("gym tomorrow at 09:00", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(c0900.count, 1)
        XCTAssertEqual(c0900[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0))
    }

    func testRelativeTimesGiveOneChip() {
        let twoHours = composerDateSuggestions("call in 2 hours", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(twoHours.count, 1)
        XCTAssertEqual(twoHours[0].alertAt, zonedDate(2026, 9, 18, 16, 0, 0))
        XCTAssertEqual(twoHours[0].text, "Call")

        let halfHour = composerDateSuggestions("call in half an hour", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(halfHour.count, 1)
        XCTAssertEqual(halfHour[0].alertAt, zonedDate(2026, 9, 18, 14, 30, 0))
        XCTAssertEqual(halfHour[0].text, "Call")

        XCTAssertEqual(composerDateSuggestions("call in 10 minutes", now: now, calendar: istanbulCalendar).count, 1)
        XCTAssertEqual(composerDateSuggestions("call in 10 mins", now: now, calendar: istanbulCalendar).count, 1)
        XCTAssertEqual(composerDateSuggestions("call in an hour", now: now, calendar: istanbulCalendar).count, 1)
        XCTAssertEqual(composerDateSuggestions("call in a minute", now: now, calendar: istanbulCalendar).count, 1)
        let oneHrThirty = composerDateSuggestions("call in 1 hour 30 minutes", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(oneHrThirty.count, 1)
        XCTAssertEqual(oneHrThirty[0].alertAt, zonedDate(2026, 9, 18, 15, 30, 0))
    }

    func testTonightAndDaypartsGiveDateHintAndTimeOfDayWithoutAlertAt() {
        let tonight = composerDateSuggestions("dinner tonight", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(tonight.count, 1)
        XCTAssertNil(tonight[0].alertAt)
        XCTAssertEqual(tonight[0].dateHint, localDay(2026, 9, 18))
        XCTAssertEqual(tonight[0].timeOfDay, .night)
        XCTAssertEqual(tonight[0].text, "Dinner")

        let tomorrowEve = composerDateSuggestions("gym tomorrow evening", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(tomorrowEve.count, 1)
        XCTAssertNil(tomorrowEve[0].alertAt)
        XCTAssertEqual(tomorrowEve[0].dateHint, localDay(2026, 9, 19))
        XCTAssertEqual(tomorrowEve[0].timeOfDay, .evening)
        XCTAssertEqual(tomorrowEve[0].text, "Gym")

        let thisAfternoon = composerDateSuggestions("walk this afternoon", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(thisAfternoon.count, 1)
        XCTAssertNil(thisAfternoon[0].alertAt)
        XCTAssertEqual(thisAfternoon[0].dateHint, localDay(2026, 9, 18))
        XCTAssertEqual(thisAfternoon[0].timeOfDay, .noon)
    }

    func testWeekdaysAndNextFridayRollover() {
        let fri6pm = composerDateSuggestions("friday 6pm gym", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(fri6pm.count, 1)
        XCTAssertEqual(fri6pm[0].alertAt, zonedDate(2026, 9, 18, 18, 0, 0))
        XCTAssertEqual(fri6pm[0].text, "Gym")

        let fri1pm = composerDateSuggestions("friday 1pm gym", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(fri1pm.count, 1)
        XCTAssertEqual(fri1pm[0].alertAt, zonedDate(2026, 9, 25, 13, 0, 0))
        XCTAssertEqual(fri1pm[0].text, "Gym")

        let nextFri = composerDateSuggestions("next friday gym", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(nextFri.count, 1)
        XCTAssertNil(nextFri[0].alertAt)
        XCTAssertEqual(nextFri[0].dateHint, localDay(2026, 9, 25))
        XCTAssertEqual(nextFri[0].text, "Gym")

        let nextWeek = composerDateSuggestions("dentist next week", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(nextWeek.count, 1)
        XCTAssertEqual(nextWeek[0].dateHint, localDay(2026, 9, 25))
    }

    func testSpecificDatesWithMonthNamesAndOrdinals() {
        let mar12 = composerDateSuggestions("party march 12", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(mar12.count, 1)
        XCTAssertNil(mar12[0].alertAt)
        XCTAssertEqual(mar12[0].dateHint, localDay(2027, 3, 12))
        XCTAssertEqual(mar12[0].text, "Party")

        let mar12At8 = composerDateSuggestions("party 12 march at 8pm", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(mar12At8.count, 1)
        XCTAssertEqual(mar12At8[0].alertAt, zonedDate(2027, 3, 12, 20, 0, 0))
        XCTAssertEqual(mar12At8[0].text, "Party")

        XCTAssertEqual(composerDateSuggestions("party mar 12th", now: now, calendar: istanbulCalendar).first?.dateHint, localDay(2027, 3, 12))
        XCTAssertEqual(composerDateSuggestions("party the 12th of march", now: now, calendar: istanbulCalendar).first?.dateHint, localDay(2027, 3, 12))
    }

    func testRepeatsDailyWeeklyMonthlyYearly() {
        let daily = composerDateSuggestions("meds every day at 9pm", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(daily.count, 1)
        XCTAssertEqual(daily[0].repeatRule, .daily)
        XCTAssertEqual(daily[0].label, "\(t("Her gün")) 21:00")
        XCTAssertEqual(daily[0].text, "Meds")

        let weekly = composerDateSuggestions("every friday at 6pm gym", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(weekly.count, 1)
        XCTAssertEqual(weekly[0].repeatRule, .weekly)
        XCTAssertEqual(weekly[0].text, "Gym")

        let monthly = composerDateSuggestions("rent every month on the 15th", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(monthly.count, 1)
        XCTAssertEqual(monthly[0].repeatRule, .monthly)
        XCTAssertEqual(monthly[0].dateHint, localDay(2026, 10, 15))
        XCTAssertEqual(monthly[0].text, "Rent")
    }

    func testOnlyDatePhraseLeavesEmptyText() {
        let chips = composerDateSuggestions("tomorrow at 9", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 2)
        XCTAssertEqual(chips[0].text, "")
        XCTAssertEqual(chips[1].text, "")
    }

    func testTurkishAndOrdinaryWordGuardsProduceNoChips() {
        let guarded = [
            "at al",
            "at 5",
            "5 kişi geldi",
            "may be late",
            "march on",
            "sat down",
            "on kişi",
            "in yapılacak"
        ]
        for phrase in guarded {
            XCTAssertTrue(composerDateSuggestions(phrase, now: now, calendar: istanbulCalendar).isEmpty, "Expected no chips for: \(phrase)")
        }
    }

    func testTurkishSentenceStillWins() {
        let chips = composerDateSuggestions("yarın 9'da spor", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(chips.count, 2)
        XCTAssertEqual(chips[0].label, "Yarın 09:00")
        XCTAssertEqual(chips[1].label, "Yarın 21:00")
        XCTAssertEqual(chips[0].text, "Spor")
    }

    func testFarDateLabelUsesEnglishMonthInEnglishUIAndTurkishMonthInTurkishUI() {
        UserDefaults.standard.set("tr", forKey: I18n.defaultsKey)
        let trChips = composerDateSuggestions("party november 12", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(trChips.count, 1)
        XCTAssertEqual(trChips[0].label, "12 Kas")

        UserDefaults.standard.set("en", forKey: I18n.defaultsKey)
        let enChips = composerDateSuggestions("party november 12", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(enChips.count, 1)
        XCTAssertEqual(enChips[0].label, "12 Nov")
    }

    func testBareWeeklyMonthlyYearlyRepeatsWithoutDayOrClockGiveNoChips() {
        XCTAssertTrue(composerDateSuggestions("meeting every week", now: now, calendar: istanbulCalendar).isEmpty)
        XCTAssertTrue(composerDateSuggestions("rent every month", now: now, calendar: istanbulCalendar).isEmpty)
        XCTAssertTrue(composerDateSuggestions("party every year", now: now, calendar: istanbulCalendar).isEmpty)
    }

    func testEveryFridayWithoutTimeKeepsWeekdayDateHintAndWeeklyRepeatWithoutAlertAt() {
        let fri = composerDateSuggestions("every friday gym", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(fri.count, 1)
        XCTAssertEqual(fri[0].repeatRule, .weekly)
        XCTAssertEqual(fri[0].dateHint, localDay(2026, 9, 18))
        XCTAssertNil(fri[0].alertAt)
        XCTAssertEqual(fri[0].text, "Gym")

        let daily = composerDateSuggestions("meds every day", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(daily.count, 1)
        XCTAssertEqual(daily[0].repeatRule, .daily)
        XCTAssertEqual(daily[0].dateHint, localDay(2026, 9, 18))
        XCTAssertNil(daily[0].alertAt)
        XCTAssertEqual(daily[0].text, "Meds")

        let everyMorning = composerDateSuggestions("meds every morning", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(everyMorning.count, 1)
        XCTAssertEqual(everyMorning[0].repeatRule, .daily)
        XCTAssertEqual(everyMorning[0].dateHint, localDay(2026, 9, 18))
        XCTAssertEqual(everyMorning[0].timeOfDay, .morning)
        XCTAssertNil(everyMorning[0].alertAt)
        XCTAssertEqual(everyMorning[0].text, "Meds")
    }

    func testTurkishCapitalDottedIPreservesOffsetsWhenRemovingEnglishDatePhrase() {
        let ilac = composerDateSuggestions("İlaç tomorrow at 9", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(ilac.count, 2)
        XCTAssertEqual(ilac[0].text, "İlaç")
        XCTAssertEqual(ilac[1].text, "İlaç")

        let izmir = composerDateSuggestions("İzmir trip friday 6pm", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(izmir.count, 1)
        XCTAssertEqual(izmir[0].text, "İzmir trip")
    }

    func testDottedAmAndPmGiveOneChipAndConsumeTrailingDot() {
        let pm = composerDateSuggestions("gym tomorrow at 5 p.m.", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(pm.count, 1)
        XCTAssertEqual(pm[0].alertAt, zonedDate(2026, 9, 19, 17, 0, 0))
        XCTAssertEqual(pm[0].text, "Gym")

        let amSharp = composerDateSuggestions("gym tomorrow at 9 a.m. sharp", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(amSharp.count, 1)
        XCTAssertEqual(amSharp[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0))
        XCTAssertEqual(amSharp[0].text, "Gym sharp")

        let pmNoTrailingDot = composerDateSuggestions("gym tomorrow at 5 p.m", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(pmNoTrailingDot.count, 1)
        XCTAssertEqual(pmNoTrailingDot[0].alertAt, zonedDate(2026, 9, 19, 17, 0, 0))
        XCTAssertEqual(pmNoTrailingDot[0].text, "Gym")

        let amNoTrailingDot = composerDateSuggestions("gym tomorrow at 9 a.m sharp", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(amNoTrailingDot.count, 1)
        XCTAssertEqual(amNoTrailingDot[0].alertAt, zonedDate(2026, 9, 19, 9, 0, 0))
        XCTAssertEqual(amNoTrailingDot[0].text, "Gym sharp")
    }

    func testMonthlyDayOfMonthMissingInSeptemberMirrorsTurkishParser() {
        let m31 = composerDateSuggestions("rent every month on the 31st", now: now, calendar: istanbulCalendar)
        XCTAssertEqual(m31.count, 1)
        XCTAssertEqual(m31[0].repeatRule, .monthly)
        XCTAssertEqual(m31[0].dateHint, localDay(2026, 9, 18))
        XCTAssertNil(m31[0].alertAt)
        XCTAssertEqual(m31[0].text, "Rent")
    }
}


