import XCTest

/// Launches the app with the demo dataset on each screen and attaches a screenshot.
/// CI exports these attachments from the .xcresult bundle.
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = true
    }

    private func capture(_ screen: String, settle: TimeInterval = 3) {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", screen]
        app.launch()
        _ = app.wait(for: .runningForeground, timeout: 20)
        Thread.sleep(forTimeInterval: settle)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = screen
        attachment.lifetime = .keepAlways
        add(attachment)
        app.terminate()
    }

    func test1Home() { capture("home") }
    func test2CustomCategory() { capture("category") }
    func test3BuiltInCategory() { capture("builtin") }
    func test4Detail() { capture("detail") }
    func test5Checklist() { capture("checklist") }
    func test6Planner() { capture("planner", settle: 4) }

    func test6bPlanner24() { capture("planner24", settle: 3) }

    func test17Pano() { capture("pano", settle: 3) }

    func test18PanoBoard() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "pano"]
        app.launch()
        let first = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'OLMUYO'")).firstMatch
        if first.waitForExistence(timeout: 10) { first.tap() }
        Thread.sleep(forTimeInterval: 2)
        shot("panoboard")
    }

    func test19Books() { capture("books", settle: 3) }
    func test20Reader() { capture("reader", settle: 3) }

    func test21BookDetail() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "books"]
        app.launch()
        let book = app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Limandaki Defter'")).firstMatch
        if book.waitForExistence(timeout: 10) { book.tap() }
        Thread.sleep(forTimeInterval: 2)
        shot("bookdetail")
    }

    func test22Film() { capture("film", settle: 3) }
    func test23FilmDeck() { capture("filmdeck", settle: 3) }
    func test24Game() { capture("game", settle: 3) }

    func test25Moods() { capture("moods", settle: 2) }

    func test15Yasam() { capture("yasam", settle: 3) }
    func test16Water() { capture("water", settle: 3) }

    func test8Goal() { capture("goal", settle: 4) }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func test9DetailEdit() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "detail"]
        app.launch()
        let edit = app.buttons["Düzenle"]
        if edit.waitForExistence(timeout: 10) { edit.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("detailedit")
        let color = app.buttons["Renk ve kategori"]
        if color.waitForExistence(timeout: 5) { color.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("detailcolor")
    }

    func test10DetailScrolled() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "detail"]
        app.launch()
        Thread.sleep(forTimeInterval: 2)
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1.2)
        shot("detailblocks")
    }

    func test11DockNavigation() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "home"]
        app.launch()
        Thread.sleep(forTimeInterval: 2)
        // Drag up from the composer capsule (dock row ~92% of the screen height).
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.915))
        start.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.80)))
        Thread.sleep(forTimeInterval: 1.2)
        shot("docknav")
    }

    func test12CategoryProgress() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "category"]
        app.launch()
        let ring = app.buttons["Kategori ilerlemesini göster"]
        if ring.waitForExistence(timeout: 10) { ring.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("categoryprogress")
    }

    func test13Pickers() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "home"]
        app.launch()
        let field = app.textFields.firstMatch.exists ? app.textFields.firstMatch : app.textViews.firstMatch
        if field.waitForExistence(timeout: 10) { field.tap(); field.typeText("Toplantı") }
        let date = app.buttons["Tarih seç"]
        if date.waitForExistence(timeout: 5) { date.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("datepicker")
        let cancel = app.buttons["İptal"]
        if cancel.waitForExistence(timeout: 3) { cancel.tap() }
        Thread.sleep(forTimeInterval: 1)
        let cat = app.buttons["Kategori seç"]
        if cat.waitForExistence(timeout: 5) { cat.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("categorypicker")
    }

    func test14CategoryEditor() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "category"]
        app.launch()
        let edit = app.buttons["Kategoriyi düzenle"]
        if edit.waitForExistence(timeout: 10) { edit.tap() }
        Thread.sleep(forTimeInterval: 1.5)
        shot("categoryeditor")
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1)
        shot("categoryeditor2")
    }

    func test7ComposerExpanded() {
        let app = XCUIApplication()
        app.launchArguments = ["-REALDemo", "-REALScreen", "home"]
        app.launch()
        let field = app.textFields.firstMatch.exists ? app.textFields.firstMatch : app.textViews.firstMatch
        if field.waitForExistence(timeout: 10) {
            field.tap()
            field.typeText("Yarın 10'da toplantı")
        }
        Thread.sleep(forTimeInterval: 1.5)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "composer"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
