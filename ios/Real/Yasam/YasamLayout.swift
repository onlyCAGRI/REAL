import SwiftUI
import Observation

/// YasamLayout.kt: Me's parts. `counter` parts are the "Bugün" tiles (numbers), `discover` parts the Keşfet tiles
/// (lists); Günlüklerim is a tile in the Bugün grid (top by default). Default order = the approved draft.
enum YasamPart: String, CaseIterable, Identifiable {
    case steps, coffee, sleep, mood, water, alcohol, journal, watch, play, listen, book, rate

    var id: String { rawValue }

    var label: String {
        switch self {
        case .steps: return t("Adım")
        case .coffee: return t("Kahve")
        case .sleep: return t("Uyku")
        case .mood: return "Mood"
        case .water: return t("Su")
        case .alcohol: return t("Alkol")
        case .journal: return t("Günlüklerim")
        case .watch: return t("İzle")
        case .play: return t("Oyna")
        case .listen: return t("Dinle")
        case .book: return t("Kitap")
        case .rate: return t("Puanla")
        }
    }

    /// "+ Oku" chips and tiles call the book part "Oku".
    var chipLabel: String { self == .book ? t("Oku") : label }

    var tone: Color {
        switch self {
        case .steps: return Color(argb: 0xFF7C86FF)
        case .coffee: return Color(argb: 0xFFD9A066)
        case .sleep: return Color(argb: 0xFFA58AF0)
        case .mood: return YasamTone.moodDefault
        case .water: return YasamTone.water
        case .alcohol: return Color(argb: 0xFFFF8A4C)
        case .journal: return YasamTone.journal
        case .watch: return Color(argb: 0xFF2F6BFF)
        case .play: return Color(argb: 0xFFF26A1B)
        case .listen: return Color(argb: 0xFFC06CFF)
        case .book: return YasamTone.book
        case .rate: return Color(argb: 0xFFB9D47A)
        }
    }

    var discover: Bool { [.watch, .play, .listen, .book, .rate].contains(self) }
    var counter: Bool { [.steps, .coffee, .sleep, .mood, .water, .alcohol].contains(self) }

    /// YasamGlow.kt: a tile's colours (base, light spot, deep spot).
    var glow: YasamGlowTone {
        switch self {
        case .steps: return YasamGlowTone(0xFF1A1D6E, 0xFF4B55FF, 0xFF9A7BFF)
        case .sleep: return YasamGlowTone(0xFF3A3478, 0xFF6F63C9, 0xFF0B0B18)
        case .water: return YasamGlowTone(0xFF1B4FB8, 0xFF3A8CFF, 0xFF08122A)
        case .coffee: return YasamGlowTone(0xFF6B3C22, 0xFFB9784A, 0xFF2A160B)
        case .alcohol: return YasamGlowTone(0xFFC8512E, 0xFFFF8A4C, 0xFF2A3CFF)
        case .mood: return YasamGlowTone(0xFF8E4A86, 0xFFF08CC8, 0xFF2C0C22)
        case .journal: return YasamGlowTone(0xFF2C4A40, 0xFF8FB7A6, 0xFF0E1A16)
        case .watch: return YasamGlowTone(0xFF1A2F6E, 0xFF3B6FD8, 0xFF05070F)
        case .play: return YasamGlowTone(0xFFC2541F, 0xFFFFB35C, 0xFF3A1406)
        case .listen: return YasamGlowTone(0xFF5A2A8A, 0xFFC06CFF, 0xFF1A0624)
        case .book: return YasamGlowTone(0xFF6A4A14, 0xFFE8B04A, 0xFF1E1404)
        case .rate: return YasamGlowTone(0xFF55611A, 0xFFD8E86A, 0xFF151A05)
        }
    }
}

struct YasamGlowTone {
    let base: Color
    let light: Color
    let deep: Color
    var text: Color = .white

    init(_ base: UInt32, _ light: UInt32, _ deep: UInt32) {
        self.base = Color(argb: base); self.light = Color(argb: light); self.deep = Color(argb: deep)
    }
}

extension View {
    /// Modifier.yasamGlow: a base colour with two soft light spots (plain radial gradients, no blur pass).
    func yasamGlow(_ tone: YasamGlowTone) -> some View {
        background {
            GeometryReader { g in
                let r = max(g.size.width, g.size.height)
                ZStack {
                    tone.base
                    RadialGradient(colors: [tone.light, .clear], center: UnitPoint(x: 0.18, y: 0.2), startRadius: 0, endRadius: r * 0.75)
                    RadialGradient(colors: [tone.deep, .clear], center: UnitPoint(x: 0.85, y: 0.9), startRadius: 0, endRadius: r * 0.7)
                }
            }
        }
    }
}

/// Order, hidden parts, full-width tiles, Me's name and on/off. The Me screen (hold to edit), Düzenle and Settings
/// read and write the same record.
@Observable
final class YasamLayout {
    static let shared = YasamLayout()
    static let defaultName = "Me"
    static let nameMax = 10
    private static let orderKey = "yasam_layout_order"
    private static let hiddenKey = "yasam_layout_hidden"
    private static let wideKey = "yasam_layout_wide"
    private static let pickedKey = "yasam_picked_v1"
    private static let nameKey = "yasam_name"
    private static let enabledKey = "yasam_enabled"

    private(set) var order: [YasamPart]
    private(set) var hidden: Set<YasamPart>
    private(set) var wide: Set<YasamPart>
    /// The "what should Me show" picker ran once (also for people who had Yaşam before the tiles).
    private(set) var picked: Bool
    private(set) var name: String
    /// Off: Me leaves home (the word by the logo). Its data stays.
    private(set) var enabled: Bool

    var visible: [YasamPart] { order.filter { !hidden.contains($0) } }

    private init() {
        let d = UserDefaults.standard
        order = Self.merged(Self.parse(d.string(forKey: Self.orderKey)))
        hidden = Set(Self.parse(d.string(forKey: Self.hiddenKey)))
        wide = d.string(forKey: Self.wideKey).map { Set(Self.parse($0)) } ?? [.steps]
        picked = d.bool(forKey: Self.pickedKey) || ProcessInfo.processInfo.arguments.contains("-REALDemo")
        name = d.string(forKey: Self.nameKey).flatMap { $0.isEmpty ? nil : $0 } ?? Self.defaultName
        enabled = d.object(forKey: Self.enabledKey) as? Bool ?? true
        // Once: order and sizes go back to the draft's layout (older builds saved the old row order).
        if !d.bool(forKey: "yasam_layout_v2") {
            order = YasamPart.allCases
            wide = [.steps]
            d.set(true, forKey: "yasam_layout_v2")
            persist()
        }
        // Once: Günlüklerim became a tile; it goes to the top, full width.
        if !d.bool(forKey: "yasam_journal_tile_v1") {
            order = [.journal] + order.filter { $0 != .journal }
            wide.insert(.journal)
            d.set(true, forKey: "yasam_journal_tile_v1")
            persist()
        }
    }

    private func persist() {
        let d = UserDefaults.standard
        d.set(order.map(\.rawValue).joined(separator: ","), forKey: Self.orderKey)
        d.set(hidden.map(\.rawValue).joined(separator: ","), forKey: Self.hiddenKey)
        d.set(wide.map(\.rawValue).joined(separator: ","), forKey: Self.wideKey)
        d.set(picked, forKey: Self.pickedKey)
        d.set(name, forKey: Self.nameKey)
        d.set(enabled, forKey: Self.enabledKey)
    }

    func save(order newOrder: [YasamPart], hidden newHidden: Set<YasamPart>) {
        order = Self.merged(newOrder)
        hidden = newHidden
        persist()
    }

    /// Drag on the Me screen: [part] takes [target]'s place.
    func move(_ part: YasamPart, to target: YasamPart) {
        guard part != target, let to = order.firstIndex(of: target), let from = order.firstIndex(of: part) else { return }
        var list = order
        list.remove(at: from)
        list.insert(part, at: min(to, list.count))
        order = list
        persist()
    }

    func setHidden(_ part: YasamPart, _ hide: Bool) {
        if hide { hidden.insert(part) } else { hidden.remove(part) }
        persist()
    }

    func setWide(_ part: YasamPart, _ full: Bool) {
        if full { wide.insert(part) } else { wide.remove(part) }
        persist()
    }

    /// First-run picker: what is chosen shows, the rest hides.
    func pick(_ chosen: Set<YasamPart>) {
        hidden = Set(YasamPart.allCases).subtracting(chosen)
        picked = true
        persist()
    }

    func rename(_ value: String) {
        let trimmed = String(value.trimmingCharacters(in: .whitespacesAndNewlines).prefix(Self.nameMax))
        name = trimmed.isEmpty ? Self.defaultName : trimmed
        persist()
    }

    func setEnabled(_ on: Bool) {
        enabled = on
        persist()
    }

    private static func parse(_ raw: String?) -> [YasamPart] {
        (raw ?? "").split(separator: ",").compactMap { YasamPart(rawValue: String($0)) }
    }

    private static func merged(_ list: [YasamPart]) -> [YasamPart] {
        var seen = Set<YasamPart>()
        return (list + YasamPart.allCases).filter { seen.insert($0).inserted }
    }
}

/// Bölümleri sırala / gizle: kategori sıralama sayfasıyla (CategoryOrderSheet) aynı tasarım. Tutamaçla sırala,
/// gözle gizle, gizlenenler çip olarak geri eklenir. "Tamam" kaydeder; sayfayı kapatmak vazgeçer.
struct YasamLayoutSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var order: [YasamPart] = []
    @State private var hidden: Set<YasamPart> = []
    @State private var loaded = false

    private var visible: [YasamPart] { order.filter { !hidden.contains($0) } }
    private var hiddenList: [YasamPart] { order.filter { hidden.contains($0) } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(YasamLayout.shared.name).font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
                Spacer()
                Button {
                    YasamLayout.shared.save(order: order, hidden: hidden)
                    dismiss()
                } label: {
                    Text(t("Tamam")).font(RealFont.inter(14, .semibold)).foregroundStyle(.black)
                        .padding(.horizontal, 18).padding(.vertical, 9).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
            }
            .padding(.bottom, 8)
            List {
                ForEach(visible) { part in
                    row(part)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
                }
                .onMove { from, to in
                    var v = visible
                    v.move(fromOffsets: from, toOffset: to)
                    order = v + hiddenList
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .environment(\.editMode, .constant(.active))
            if !hiddenList.isEmpty {
                Text(t("Gizlenenler")).font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.45))
                    .padding(.top, 10)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(hiddenList) { part in
                            Button { withAnimation { _ = hidden.remove(part) } } label: {
                                Text("+ \(part.chipLabel)").font(RealFont.inter(13, .medium)).foregroundStyle(.white.opacity(0.85))
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(Capsule().fill(.white.opacity(0.07)))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 6)
                }
            }
            Button(t("Varsayılan")) {
                withAnimation {
                    order = YasamPart.allCases
                    hidden = []
                }
            }
            .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.45))
            .frame(maxWidth: .infinity)
            .padding(.top, 12)
        }
        .padding(.horizontal, 20).padding(.top, 20).padding(.bottom, 14)
        .presentationDetents([.large])
        .presentationBackground(Color(argb: 0xFF101012))
        .presentationDragIndicator(.visible)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            order = YasamLayout.shared.order
            hidden = YasamLayout.shared.hidden
        }
    }

    private func row(_ part: YasamPart) -> some View {
        HStack(spacing: 14) {
            Circle().fill(part.tone).frame(width: 12, height: 12)
            Text(part.chipLabel).font(RealFont.inter(15, .medium)).foregroundStyle(.white).lineLimit(1)
            Spacer()
            Button { withAnimation { _ = hidden.insert(part) } } label: {
                Image(systemName: "eye.slash").font(.system(size: 15)).foregroundStyle(Color(argb: 0xFFAAAAB4)).frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("{0} gizle", part.chipLabel))
        }
        .padding(.leading, 16).padding(.vertical, 8).padding(.trailing, 4)
        .frame(minHeight: 56)
        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.05)))
    }
}

/// Me's first open: "Me'de neler olsun?" Each choice is a tile that glows in its own colour when picked, so it
/// already looks like it will on Me. Başla saves; at least one is needed.
struct YasamPickScreen: View {
    var onDone: () -> Void
    @State private var chosen: Set<YasamPart> = Set(YasamLayout.shared.visible.filter { $0 != .coffee && $0 != .alcohol })

    private static let subs: [YasamPart: String] = [
        .steps: "saatten ya da elle", .sleep: "kaç saat uyudun", .water: "bardak bardak, hedefli",
        .coffee: "fincan ve kafein", .alcohol: "ne içtiğin, ne kadar", .mood: "günde bir dokunuş",
        .journal: "en üstte, günlüğün", .watch: "film ve dizi listen", .play: "oyun listen",
        .listen: "şarkı listen", .book: "okuma rafı", .rate: "izlediklerine puan",
    ]

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                Text(t("ME · İLK KURULUM")).font(.system(size: 11, design: .monospaced)).kerning(1.6).foregroundStyle(YasamTone.muted)
                    .padding(.top, 28)
                Text(t("Me'de neler olsun?")).font(RealFont.inter(30, .bold)).foregroundStyle(.white).padding(.top, 6)
                Text(t("Seçtiklerin kart olur. Sonra Düzenle'den değişir.")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
                    .padding(.top, 4).padding(.bottom, 16)
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                        ForEach([YasamPart.journal] + YasamPart.allCases.filter { $0 != .journal }) { part in
                            tile(part)
                        }
                    }
                }
                Button {
                    YasamLayout.shared.pick(chosen)
                    onDone()
                } label: {
                    Text(chosen.isEmpty ? t("En az bir tane seç") : t("Başla · {0} seçili", chosen.count))
                        .font(RealFont.inter(16, .bold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 54)
                        .background(Capsule().fill(.white.opacity(chosen.isEmpty ? 0.35 : 1)))
                }
                .buttonStyle(.plain)
                .disabled(chosen.isEmpty)
                .padding(.vertical, 14)
            }
            .padding(.horizontal, 18)
        }
    }

    private func tile(_ part: YasamPart) -> some View {
        let on = chosen.contains(part)
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return Button {
            if on { chosen.remove(part) } else { chosen.insert(part) }
            UISelectionFeedbackGenerator().selectionChanged()
        } label: {
            ZStack(alignment: .topTrailing) {
                VStack(alignment: .leading, spacing: 18) {
                    Text(part == .book ? t("Kitap") : part.label).font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                    Text(t(Self.subs[part] ?? "")).font(RealFont.inter(11)).foregroundStyle(.white.opacity(0.65))
                }
                .frame(maxWidth: .infinity, minHeight: 60, alignment: .topLeading)
                if on {
                    Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(.black)
                        .frame(width: 20, height: 20).background(Circle().fill(.white))
                }
            }
            .padding(12)
            .background {
                if on { Color.clear.yasamGlow(part.glow) } else { Color(argb: 0xFF0D0D10) }
            }
            .clipShape(shape)
            .overlay(shape.strokeBorder(on ? .clear : Color(argb: 0xFF23232A), lineWidth: 1))
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// "Adını değiştir": from Settings and from a long press on the word by the logo. Up to 10 letters.
struct YasamRenameSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var value = YasamLayout.shared.name
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(t("Adını değiştir")).font(RealFont.inter(22, .semibold)).foregroundStyle(.white)
            Text(t("Logonun yanında ve kendi ekranında bu yazar.")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
                .padding(.top, 6)
            HStack {
                TextField("", text: $value)
                    .font(RealFont.inter(18, .semibold)).foregroundStyle(.white).tint(.white)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(save)
                    .onChange(of: value) { _, v in if v.count > YasamLayout.nameMax { value = String(v.prefix(YasamLayout.nameMax)) } }
                Text("\(value.count)/\(YasamLayout.nameMax)").font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
            }
            .padding(.horizontal, 14).frame(minHeight: 50)
            .background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.07)))
            .padding(.top, 14)
            HStack(spacing: 6) {
                ForEach(Array(NSOrderedSet(array: [YasamLayout.defaultName, t("Ben"), t("Günüm"), t("Hayat")]).array as? [String] ?? []), id: \.self) { s in
                    Button { value = s } label: {
                        Text(s).font(RealFont.inter(13, .semibold)).foregroundStyle(s == value ? .black : .white)
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(Capsule().fill(s == value ? Color.white : Color.white.opacity(0.08)))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 12)
            HStack(spacing: 10) {
                Button { dismiss() } label: {
                    Text(t("Vazgeç")).font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).frame(height: 50).background(Capsule().fill(.white.opacity(0.1)))
                }
                Button(action: save) {
                    Text(t("Kaydet")).font(RealFont.inter(15, .bold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 50).background(Capsule().fill(.white))
                }
            }
            .buttonStyle(.plain)
            .padding(.top, 20)
        }
        .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 12)
        .presentationDetents([.height(330)])
        .presentationBackground(Color(argb: 0xFF101012))
        .presentationDragIndicator(.visible)
        .onAppear { focused = true }
    }

    private func save() {
        YasamLayout.shared.rename(value)
        dismiss()
    }
}
