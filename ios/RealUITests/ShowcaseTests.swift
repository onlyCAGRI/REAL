import XCTest

/// Presentation screenshots on the calm showcase dataset (DemoData.seedShowcase).
/// CI runs only this class in "showcase" mode and uploads the attachments as the "showcase-screenshots" artifact.
/// Attachment names carry no underscores: the export step names files after the part before the first "_".
final class ShowcaseTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = true
    }

    private func launch(_ screen: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALShowcase", "-REALScreen", screen]
        app.launch()
        _ = app.wait(for: .runningForeground, timeout: 20)
        return app
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func capture(_ screen: String, as name: String? = nil, settle: TimeInterval = 3) {
        let app = launch(screen)
        Thread.sleep(forTimeInterval: settle)
        shot(name ?? screen)
        app.terminate()
    }

    func test01Home() { capture("home", as: "01-home") }

    func test02HomeDeck() {
        let app = launch("home")
        Thread.sleep(forTimeInterval: 2.5)
        let showAll = app.buttons["Tümünü göster"]
        if showAll.waitForExistence(timeout: 3) { showAll.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("02-home-categories")
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1.2)
        shot("03-home-cards")
    }

    func test03Category() { capture("category", as: "04-category") }

    func test04CategoryProgress() {
        let app = launch("category")
        let ring = app.buttons["Kategori ilerlemesini göster"]
        if ring.waitForExistence(timeout: 10) { ring.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("05-category-progress")
    }

    func test05Detail() {
        let app = launch("detail")
        Thread.sleep(forTimeInterval: 2.5)
        shot("06-detail")
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1.2)
        shot("07-detail-notes")
    }

    func test06Notes() {
        let app = launch("notes")
        Thread.sleep(forTimeInterval: 2.5)
        shot("08-notes")
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1.2)
        shot("09-notes-shared")
    }

    func test07Stack() { capture("stack", as: "10-card-stack") }
    func test08Reading() { capture("reading", as: "11-reading-notes") }
    func test09Checklist() { capture("checklist", as: "12-checklist") }
    func test10Month() { capture("planner", as: "13-calendar-month", settle: 4) }
    func test11Day() { capture("planner24", as: "14-calendar-24h", settle: 3) }
    func test12Yasam() { capture("yasam", as: "15-yasam") }
    func test13Water() { capture("water", as: "16-water") }
    func test14Moods() { capture("moods", as: "17-moods", settle: 2) }

    func test15Composer() {
        let app = launch("home")
        let field = app.textFields.firstMatch.exists ? app.textFields.firstMatch : app.textViews.firstMatch
        if field.waitForExistence(timeout: 10) {
            field.tap()
            field.typeText("Yarın 10'da ekiple sunum provası")
        }
        Thread.sleep(forTimeInterval: 1.5)
        shot("18-composer")
    }
}
