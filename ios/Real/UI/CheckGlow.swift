import SwiftUI

/// Kontrol ettir (CheckGlow.kt): a card someone asked you to look at glows from its right edge, breathing slowly
/// (2.8 s), until you open it. "Seen" is kept on this phone per request, so a new request lights the card again.
@MainActor
@Observable
final class CheckRequests {
    static let shared = CheckRequests()
    static let selfColor = Color(argb: 0xFF6FD6FF)
    static let otherColor = Color(argb: 0xFFFFB86F)
    private static let key = "real_check_seen"

    private(set) var seen: Set<String> = Set(UserDefaults.standard.stringArray(forKey: CheckRequests.key) ?? [])

    private static func id(_ r: ReminderItem) -> String { "\(r.id):\(r.checkRequest?.at ?? 0)" }

    /// Opening the card puts its light out (on this phone).
    func markSeen(_ r: ReminderItem) {
        guard r.checkRequest != nil, !seen.contains(Self.id(r)) else { return }
        seen.insert(Self.id(r))
        UserDefaults.standard.set(Array(seen.suffix(400)), forKey: Self.key)
    }

    /// The light this viewer should see on `r`, or nil.
    func glow(_ r: ReminderItem, me: String?, today: Date = Date()) -> Color? {
        guard let c = r.checkRequest, !r.isCompleted, !r.isDivider else { return nil }
        if let start = c.startEpochDay, Calendar.real.epochDay(of: today) < start { return nil }
        let forMe: Bool
        if r.sharedCardId == nil {
            forMe = c.to.contains(CheckRequest.me) || (me.map(c.to.contains) ?? false)
        } else if let me {
            forMe = c.to.contains(me) || (c.to.contains(CheckRequest.everyone) && c.from != me) || (c.to.contains(CheckRequest.me) && c.from == me)
        } else { forMe = false }
        guard forMe, !seen.contains(Self.id(r)) else { return nil }
        if let color = c.color { return Color(argb: UInt32(bitPattern: Int32(truncatingIfNeeded: color))) }
        let mine = c.from == nil || c.from == me || (r.sharedCardId == nil && c.to.contains(CheckRequest.me))
        return mine ? Self.selfColor : Self.otherColor
    }
}

/// The breathing light along the card's right edge (about a third of the width, 12 % ↔ 55 %).
struct CheckGlowOverlay: View {
    let color: Color
    @State private var bright = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { g in
            LinearGradient(stops: [.init(color: .clear, location: 0), .init(color: color.opacity(0.35), location: 0.62),
                                   .init(color: color, location: 1)], startPoint: .leading, endPoint: .trailing)
                .frame(width: g.size.width * 0.34)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .opacity(reduceMotion ? 0.55 : (bright ? 0.55 : 0.12))
        }
        .allowsHitTesting(false)
        .onAppear { withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { bright = true } }
    }
}

/// One person the request can go to ("me" for yourself, "*" for all members).
struct CheckTarget: Identifiable, Hashable { let key: String; let label: String; var id: String { key } }

/// Kontrol ettir (from the selection bar): who should look, from when, and the colour ("Otomatik": blue for yourself,
/// amber for someone else). `canClear`: a selected card already has a request ("Kontrolü kaldır").
struct CheckRequestSheet: View {
    let targets: [CheckTarget]
    let canClear: Bool
    let onSave: (_ target: String, _ startEpochDay: Int64?, _ color: Int?) -> Void
    let onClear: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var target = ""
    @State private var useDate = false
    @State private var day = Date()
    @State private var color: Int?
    private let swatches: [UInt32] = [0xFFFF8FB1, 0xFF7BE0A4, 0xFFB59BFF]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Kontrol ettir").font(RealFont.inter(17, .semibold)).foregroundStyle(Color(argb: 0xFFF5F5F8))
            label("KİME")
            chips { ForEach(targets) { t in chip(t.label, target == t.key) { target = t.key } } }
            label("NE ZAMAN YANSIN")
            chips {
                chip("Şimdi", !useDate) { useDate = false }
                chip("Tarih", useDate) { useDate = true }
            }
            if useDate {
                DatePicker("", selection: $day, in: Date()..., displayedComponents: .date).labelsHidden().datePickerStyle(.compact)
                    .environment(\.locale, Locale(identifier: "tr_TR"))
            }
            label("RENK")
            chips {
                chip("Otomatik", color == nil, swatch: AnyShapeStyle(LinearGradient(stops: [.init(color: CheckRequests.selfColor, location: 0.5),
                    .init(color: CheckRequests.otherColor, location: 0.5)], startPoint: .leading, endPoint: .trailing))) { color = nil }
                ForEach(swatches, id: \.self) { argb in
                    chip("", color == Int(Int32(bitPattern: argb)), swatch: AnyShapeStyle(Color(argb: argb))) { color = Int(Int32(bitPattern: argb)) }
                }
            }
            HStack(spacing: 10) {
                Button { if canClear { onClear() }; dismiss() } label: {
                    Text(canClear ? "Kontrolü kaldır" : "Vazgeç").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFE9E9EF))
                        .frame(maxWidth: .infinity, minHeight: 46).background(Capsule().fill(.white.opacity(0.08)))
                }
                Button { onSave(target, useDate ? Calendar.real.epochDay(of: day) : nil, color); dismiss() } label: {
                    Text("Işığı yak").font(RealFont.inter(14, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity, minHeight: 46).background(Capsule().fill(.white))
                }
            }
            .buttonStyle(PressScaleStyle(scale: 0.97))
            .padding(.top, 4)
        }
        .padding(20)
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(argb: 0xFF16161A))
        .presentationDragIndicator(.visible)
        .onAppear { if target.isEmpty { target = targets.first?.key ?? CheckRequest.me } }
    }

    private func label(_ text: String) -> some View {
        Text(text).font(RealFont.inter(11)).kerning(0.5).foregroundStyle(Color(argb: 0xFF8C8C95))
    }

    private func chips<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 8) { content() } }
    }

    private func chip(_ text: String, _ selected: Bool, swatch: AnyShapeStyle? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let swatch { Circle().fill(swatch).frame(width: 14, height: 14) }
                if !text.isEmpty { Text(text).font(RealFont.inter(14)).foregroundStyle(.white) }
            }
            .padding(.horizontal, 14).frame(height: 38)
            .background(Capsule().fill(.white.opacity(selected ? 0.16 : 0.05)))
            .overlay(Capsule().strokeBorder(.white.opacity(selected ? 0.6 : 0.10), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

extension ReminderStore {
    /// Kontrol ettir targets for the selected cards: yourself, plus the people of their one shared category.
    @MainActor func checkTargets(for ids: Set<Int64>) async -> [CheckTarget] {
        let me = RealAuth.shared.account?.uid
        let categoryIds = Set(ids.compactMap { reminder($0) }.map(\.customCategoryId))
        guard categoryIds.count == 1, let only = categoryIds.first, let category = category(only), category.isShared, let me else {
            return [CheckTarget(key: CheckRequest.me, label: "Kendime")]
        }
        let others = ((try? await SharedCategorySync.shared.members(category)) ?? []).filter { $0.uid != me }
            .map { CheckTarget(key: $0.uid, label: $0.name) }
        return [CheckTarget(key: me, label: "Kendime")] + others + (others.count > 1 ? [CheckTarget(key: CheckRequest.everyone, label: "Herkes")] : [])
    }

    /// Puts (or with nil takes off) a Kontrol ettir request on the cards.
    @MainActor func setCheckRequest(_ ids: Set<Int64>, target: String?, startEpochDay: EpochDay? = nil, color: Int? = nil) {
        let request = target.map { CheckRequest(to: [$0], from: RealAuth.shared.account?.uid, at: .now, startEpochDay: startEpochDay, color: color) }
        for id in ids where reminder(id)?.isDivider == false { mutate(id) { $0.checkRequest = request; $0.updatedAt = .now } }
    }
}
