import SwiftUI
import WebKit

/// Humation avatar editor (AvatarEditorActivity.kt): the same page as Android (Resources/Avatar/avatar-editor.html,
/// a copy of Android's assets/avatar/editor.html) in a WKWebView. Saving gives a 512 px PNG + the editor state; the PNG
/// becomes the profile picture (kind "avatar", shared with your people like on Android).
struct AvatarEditorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var failed = false

    var body: some View {
        // The page runs edge to edge (its avatar stage goes up behind the status bar); the glass back button sits on it.
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            AvatarWebView(onSaved: { dismiss() }, onFailed: { failed = true })
                .ignoresSafeArea()
            Button { dismiss() } label: {
                Image(systemName: "chevron.left").font(.system(size: 17, weight: .semibold)).foregroundStyle(.black)
                    .frame(width: 44, height: 44)
            }
            .liquidGlass(Circle(), role: .chrome)
            .accessibilityLabel("Geri")
            .padding(.leading, 14).padding(.top, 6)
        }
        // Dark status-bar icons over the (usually light) avatar stage.
        .preferredColorScheme(.light)
        .alert("Avatar kaydedilemedi", isPresented: $failed) { Button("Tamam", role: .cancel) {} }
    }
}

/// Saved avatar state (the editor reopens where you left it), per account like the photo.
enum AvatarStore {
    private static var stateKey: String { "avatar_state_json_" + (RealAuth.shared.account?.uid ?? "local") }
    static var stateJSON: String {
        get { UserDefaults.standard.string(forKey: stateKey) ?? "" }
        set { UserDefaults.standard.set(newValue, forKey: stateKey) }
    }
}

private struct AvatarWebView: UIViewRepresentable {
    let onSaved: () -> Void
    let onFailed: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onSaved: onSaved, onFailed: onFailed) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "avatar")
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = .black
        web.scrollView.backgroundColor = .black
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.navigationDelegate = context.coordinator
        if let url = Bundle.main.url(forResource: "avatar-editor", withExtension: "html"),
           let html = try? String(contentsOf: url, encoding: .utf8) {
            let saved = Data(AvatarStore.stateJSON.utf8).base64EncodedString()
            // Same base URL as Android's WebView; the page loads Humation from jsDelivr.
            web.loadHTMLString(html.replacingOccurrences(of: "__SAVED_STATE__", with: saved).replacingOccurrences(of: "__TOP_INSET__", with: "0"), baseURL: URL(string: "https://humation.app/"))
        }
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "avatar")
    }

    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        let onSaved: () -> Void
        let onFailed: () -> Void
        init(onSaved: @escaping () -> Void, onFailed: @escaping () -> Void) { self.onSaved = onSaved; self.onFailed = onFailed }

        private var loaded = false
        // Only REAL's own page: after it loads, no link or redirect ever leaves it, so the "avatar" handler is never
        // exposed to another site (Android refuses every navigation the same way).
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard action.targetFrame?.isMainFrame == true else { decisionHandler(.cancel); return }
            if loaded { decisionHandler(.cancel); return }
            loaded = true
            decisionHandler(.allow)
        }

        func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any], let state = body["state"] as? String, let png = body["png"] as? String,
                  state.count <= 64 * 1024, png.count <= 3 * 1024 * 1024,
                  let comma = png.firstIndex(of: ","), let data = Data(base64Encoded: String(png[png.index(after: comma)...])),
                  data.count <= 2 * 1024 * 1024, data.starts(with: [0x89, 0x50, 0x4E, 0x47]),
                  let image = UIImage(data: data), image.size.width <= 512, image.size.height <= 512 else { onFailed(); return }
            Task { @MainActor in
                AvatarStore.stateJSON = state
                ProfilePhotoStore.save(image, kind: "avatar")
                await ProfilePictures.push()
                RealToast.shared.show("Avatar kaydedildi")
                onSaved()
            }
        }
    }
}
