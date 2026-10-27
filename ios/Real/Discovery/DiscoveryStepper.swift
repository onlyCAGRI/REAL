import SwiftUI

/// One option in a discovery step (a genre, a feeling, a platform, an era).
struct StepOption: Hashable, Identifiable {
    let key: String
    let label: String
    var id: String { key }
}

/// One question of the discovery picker (DiscoveryStepper.kt). `max` options at most; an `optional` step can be
/// skipped; `groups` splits a long list under small headings (themes); `top` is shown above the options.
struct DiscoveryStep {
    let title: String
    let subtitle: String
    let max: Int
    let optional: Bool
    let groups: [(String?, [StepOption])]
    var top: AnyView? = nil
    var keys: Set<String> { Set(groups.flatMap { $0.1.map(\.key) }) }
}

/// Discovery picker, one question at a time: a few careful choices instead of many. Options that rarely go with what
/// is chosen (`rare`) are dimmed but still work, with a one-line note; a full step dims the rest and a tap explains
/// the limit. The last step's button asks for suggestions.
struct DiscoveryStepper: View {
    let steps: [DiscoveryStep]
    @Binding var selection: Set<String>
    let accent: Color
    let loading: Bool
    let enabled: Bool
    let doneLabel: String
    /// The current question; the screen's back button steps it back (owned by the screen).
    @Binding var step: Int
    var rare: (Set<String>, String) -> Bool = { _, _ in false }
    let onDone: () -> Void
    @State private var hint: String?
    @State private var forward = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                ForEach(steps.indices, id: \.self) { i in
                    Capsule().fill(i <= step ? Color.white : Color(argb: 0xFF333336)).frame(width: 24, height: 3)
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 6)
            ScrollView {
                page(steps[step]).id(step)
                    .transition(.asymmetric(insertion: .move(edge: forward ? .trailing : .leading).combined(with: .opacity),
                                            removal: .opacity))
            }
            .scrollIndicators(.hidden)
            let s = steps[step]
            let chosenHere = selection.intersection(s.keys).count
            let last = step == steps.count - 1
            let label: String = {
                if !last && chosenHere == 0 && s.optional { return t("Atla") }
                if !last { return t("Devam") }
                if chosenHere == 0 && s.optional { return t("Atla ve {0}", doneLabel) }
                return doneLabel.prefix(1).uppercased() + doneLabel.dropFirst()
            }()
            DiscoveryLoadingButton(label: label, loading: loading && last, enabled: enabled && (s.optional || chosenHere > 0), accent: accent) {
                if last { onDone() } else { go(step + 1) }
            }
            .padding(.horizontal, 20).padding(.vertical, 14)
        }
        .onChange(of: step) { old, new in forward = new > old; hint = nil }
    }

    private func go(_ next: Int) {
        forward = next > step
        withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.32)) { step = next }
    }

    private func page(_ s: DiscoveryStep) -> some View {
        let chosen = selection.intersection(s.keys)
        return VStack(alignment: .leading, spacing: 0) {
            Text(t(s.title)).font(RealFont.inter(28, .bold)).foregroundStyle(.white).padding(.top, 10)
            Text(t(s.subtitle)).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8A8A92)).padding(.top, 6).padding(.bottom, 16)
            if let top = s.top { top.padding(.bottom, 18) }
            ForEach(Array(s.groups.enumerated()), id: \.offset) { _, group in
                if let heading = group.0 {
                    Text(t(heading)).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8A92)).padding(.top, 14).padding(.bottom, 8)
                }
                ChipFlow(spacing: 8) {
                    ForEach(group.1) { o in
                        let on = chosen.contains(o.key)
                        let full = !on && chosen.count >= s.max && s.max > 1
                        let isRare = !on && rare(selection, o.key)
                        DiscoveryChip(label: t(o.label), selected: on, enabled: !loading, accent: accent) { tap(o, s, on: on, full: full, isRare: isRare) }
                            .opacity(full || isRare ? 0.32 : 1)
                            .animation(.easeOut(duration: 0.2), value: full || isRare)
                    }
                }
            }
            Text(hint ?? " ").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF9A9AA5)).padding(.top, 12).padding(.bottom, 20)
        }
        .padding(.horizontal, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func tap(_ o: StepOption, _ s: DiscoveryStep, on: Bool, full: Bool, isRare: Bool) {
        if on { selection.remove(o.key); hint = nil; return }
        if full {
            UINotificationFeedbackGenerator().notificationOccurred(.warning)
            hint = s.max == 2 ? t("En iyi sonuçlar için 2 seçim yeterli") : t("En fazla {0} seçim", s.max)
            return
        }
        if isRare {
            let all = steps.flatMap { $0.groups.flatMap(\.1) }
            let other = all.first { selection.contains($0.key) && rare([$0.key], o.key) }?.label
            hint = other.map { t("{0} ile {1} birlikte nadir", t(o.label), t($0)) }
        } else { hint = nil }
        // A single-choice step swaps its choice.
        if s.max == 1 { selection.subtract(s.keys) }
        selection.insert(o.key)
    }
}

/// Pairs that rarely make a good deck together (both directions). Dimmed, never forbidden.
enum DiscoveryRarePairs {
    private static func pairs(_ p: [(String, String)]) -> Set<String> { Set(p.flatMap { ["\($0.0)|\($0.1)", "\($0.1)|\($0.0)"] }) }
    static let film = pairs([("family", "horror"), ("family", "war"), ("family", "crime"), ("family", "thriller"),
        ("documentary", "fantasy"), ("documentary", "science-fiction"), ("documentary", "horror"), ("documentary", "animation"),
        ("documentary", "anime"), ("documentary", "romance"), ("animation", "war"), ("animation", "horror"), ("history", "science-fiction"),
        ("romance", "horror"), ("war", "comedy")])
    static let game = pairs([("card", "racing"), ("card", "shooter"), ("card", "fighting"), ("card", "sports"), ("card", "platformer"),
        ("sports", "metroidvania"), ("sports", "horror"), ("sports", "visual-novel"), ("sports", "roguelike"), ("sports", "souls-like"),
        ("racing", "puzzle"), ("racing", "rpg"), ("racing", "horror"), ("racing", "visual-novel"), ("racing", "metroidvania"),
        ("rhythm", "strategy"), ("rhythm", "rpg"), ("rhythm", "management"), ("visual-novel", "shooter"), ("visual-novel", "fighting"),
        ("management", "fighting"), ("management", "shooter")])
    static let music = pairs([("classical", "hiphop"), ("classical", "metal"), ("classical", "reggae"), ("classical", "latin"), ("classical", "dance"),
        ("jazz", "metal"), ("metal", "reggae"), ("metal", "rnb"), ("metal", "latin"), ("country", "electronic"), ("blues", "dance")])
    static func rare(_ table: Set<String>, _ chosen: Set<String>, _ key: String) -> Bool { chosen.contains { table.contains("\($0)|\(key)") } }
}
