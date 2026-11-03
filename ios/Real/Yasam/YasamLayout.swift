import SwiftUI
import Observation

/// YasamLayout.kt: Ben ekranının bölümleri. Keşif bölümleri (`discover`) yan yana kutular olarak çizilir.
enum YasamPart: String, CaseIterable, Identifiable {
    case water, mood, journal, book, watch, play, listen, rate

    var id: String { rawValue }

    var label: String {
        switch self {
        case .water: return t("Su")
        case .mood: return "Mood"
        case .journal: return t("Günlüklerim")
        case .book: return t("Kitap")
        case .watch: return t("İzle")
        case .play: return t("Oyna")
        case .listen: return t("Dinle")
        case .rate: return t("Puanla")
        }
    }

    var tone: Color {
        switch self {
        case .water: return YasamTone.water
        case .mood: return YasamTone.moodDefault
        case .journal: return YasamTone.journal
        case .book: return YasamTone.book
        case .watch: return Color(argb: 0xFF2F6BFF)
        case .play: return Color(argb: 0xFFF26A1B)
        case .listen: return Color(argb: 0xFFE0457B)
        case .rate: return Color(argb: 0xFFB9D47A)
        }
    }

    var discover: Bool { [.watch, .play, .listen, .rate].contains(self) }
}

/// Bölüm sırası ve gizlenenler. Ben ekranındaki "Düzenle" ile Ayarlar aynı kaydı okur ve yazar.
@Observable
final class YasamLayout {
    static let shared = YasamLayout()
    private static let orderKey = "yasam_layout_order"
    private static let hiddenKey = "yasam_layout_hidden"

    private(set) var order: [YasamPart]
    private(set) var hidden: Set<YasamPart>

    var visible: [YasamPart] { order.filter { !hidden.contains($0) } }

    private init() {
        let d = UserDefaults.standard
        // Sonradan eklenen bölümler kayıtlı sıranın sonuna gelir.
        order = Self.merged(Self.parse(d.string(forKey: Self.orderKey)))
        hidden = Set(Self.parse(d.string(forKey: Self.hiddenKey)))
    }

    func save(order newOrder: [YasamPart], hidden newHidden: Set<YasamPart>) {
        order = Self.merged(newOrder)
        hidden = newHidden
        UserDefaults.standard.set(order.map(\.rawValue).joined(separator: ","), forKey: Self.orderKey)
        UserDefaults.standard.set(hidden.map(\.rawValue).joined(separator: ","), forKey: Self.hiddenKey)
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
                Text(t("Ben")).font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
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
                                Text("+ \(part.label)").font(RealFont.inter(13, .medium)).foregroundStyle(.white.opacity(0.85))
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
            Text(part.label).font(RealFont.inter(15, .medium)).foregroundStyle(.white).lineLimit(1)
            Spacer()
            Button { withAnimation { _ = hidden.insert(part) } } label: {
                Image(systemName: "eye.slash").font(.system(size: 15)).foregroundStyle(Color(argb: 0xFFAAAAB4)).frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(t("{0} gizle", part.label))
        }
        .padding(.leading, 16).padding(.vertical, 8).padding(.trailing, 4)
        .frame(minHeight: 56)
        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.05)))
    }
}
