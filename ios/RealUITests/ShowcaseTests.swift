import XCTest

/// Store screenshots on the showcase dataset (DemoData.seedShowcase), once per store language.
/// Each screen is taken in Turkish and English (-app_language tr|en); names end in -tr / -en.
/// CI runs only this class in "showcase" mode and uploads the attachments as the "showcase-screenshots" artifact.
/// Attachment names carry no underscores: the export step names files after the part before the first "_".
final class ShowcaseTests: XCTestCase {
    private let languages = ["tr", "en"]

    override func setUp() {
        continueAfterFailure = true
    }

    private func launch(_ screen: String, _ lang: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALShowcase", "-REALScreen", screen, "-app_language", lang]
        app.launch()
        _ = app.wait(for: .runningForeground, timeout: 20)
        return app
    }

    private func shot(_ name: String, _ lang: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "\(name)-\(lang)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func capture(_ screen: String, as name: String, settle: TimeInterval = 3) {
        for lang in languages {
            let app = launch(screen, lang)
            Thread.sleep(forTimeInterval: settle)
            shot(name, lang)
            app.terminate()
        }
    }

    func test01Home() { capture("home", as: "01-home") }

    func test02HomeDeck() {
        for lang in languages {
            let app = launch("home", lang)
            Thread.sleep(forTimeInterval: 2.5)
            let showAll = app.buttons[lang == "en" ? "Show all" : "Tümünü göster"]
            if showAll.waitForExistence(timeout: 3) { showAll.tap() }
            Thread.sleep(forTimeInterval: 1.5)
            shot("02-home-categories", lang)
            app.swipeUp()
            Thread.sleep(forTimeInterval: 1.2)
            shot("03-home-cards", lang)
            app.terminate()
        }
    }

    func test03Category() { capture("category", as: "04-category") }

    func test04Detail() {
        for lang in languages {
            let app = launch("detail", lang)
            Thread.sleep(forTimeInterval: 4)
            shot("05-hero-card", lang)
            app.swipeUp()
            Thread.sleep(forTimeInterval: 1.2)
            shot("06-hero-card-more", lang)
            app.terminate()
        }
    }

    func test05Notes() { capture("notes", as: "07-trip-notes") }
    func test06Stack() { capture("stack", as: "08-card-stack") }
    func test07Checklist() { capture("checklist", as: "09-checklist") }
    func test08Month() { capture("planner", as: "10-calendar-month", settle: 4) }
    func test09Day() { capture("planner24", as: "11-calendar-24h", settle: 3) }
    func test10Pano() { capture("pano", as: "12-pano", settle: 3) }
    func test11FilmDeck() { capture("filmdeck", as: "13-discovery", settle: 4) }
    func test12Moods() { capture("moods", as: "14-moods", settle: 2) }
    func test13Water() { capture("water", as: "15-water") }
    func test14Yasam() { capture("yasam", as: "16-yasam") }

    func test15Composer() {
        for lang in languages {
            let app = launch("home", lang)
            let field = app.textFields.firstMatch.exists ? app.textFields.firstMatch : app.textViews.firstMatch
            if field.waitForExistence(timeout: 10) {
                field.tap()
                field.typeText(lang == "en" ? "Dentist tomorrow at three" : "Yarın üçte dişçi")
            }
            Thread.sleep(forTimeInterval: 1.5)
            shot("17-composer", lang)
            app.terminate()
        }
    }
}
