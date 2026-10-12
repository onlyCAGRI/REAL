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
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95))
        start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.82)))
        Thread.sleep(forTimeInterval: 1.2)
        shot("docknav")
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
