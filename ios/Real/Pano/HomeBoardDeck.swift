import SwiftUI

/// Pano news: the "3 yeni" pill and the boards dot under the home deck.
let panoNewsColor = Color(argb: 0xFFFF5A4E)
private let deckInk = Color(argb: 0xFF151517)

/// What this phone has already seen on each board: block id → content version (PanoSeen.kt). A block someone else
/// added, or whose content changed, since the board was last open counts as new; moving a block does not. The first
/// time a board is met everything on it is taken as seen, so an update does not light up old boards.
@MainActor @Observable
final class PanoSeen {
    static let shared = PanoSeen()
    /// Bumped on every mark so the home deck recounts.
    private(set) var version = 0

    private var uid: String { RealAuth.shared.account?.uid ?? "guest" }
    private func key(_ board: String) -> String { "pano_seen_v1/\(uid)/\(board)" }
    private func snapshot(_ items: [PanoItem]) -> [String: Int] {
        items.reduce(into: [:]) { $0[$1.id] = $1.contentVersion ?? 0 }
    }

    func mark(board: String, items: [PanoItem]) {
        let snap = snapshot(items)
        if let old = UserDefaults.standard.dictionary(forKey: key(board)) as? [String: Int], old == snap { return }
        UserDefaults.standard.set(snap, forKey: key(board))
        version += 1
    }

    /// Safe inside a body: a first-seen board is stored quietly, without bumping `version`.
    func unread(board: String, items: [PanoItem]) -> Int {
        guard let seen = UserDefaults.standard.dictionary(forKey: key(board)) as? [String: Int] else {
            UserDefaults.standard.set(snapshot(items), forKey: key(board))
            return 0
        }
        let me = uid
        return items.filter { item in
            guard let author = item.authorUid, author != me else { return false }
            return (seen[item.id] ?? -1) < (item.contentVersion ?? 0)
        }.count
    }

    /// Unread count per board, for the deck and the dots.
    func unread(_ pano: PanoStore) -> [String: Int] {
        _ = version
        return pano.boards.reduce(into: [:]) { $0[$1.id] = unread(board: $1.id, items: pano.items(of: $1.id)) }
    }
}

/// Home: the boards page next to the category deck (swipe the deck left). Same stacked card language as the
/// categories and the same "Tümünü göster": "Tüm panolar" opens the deck in place, with "Yeni pano" under it.
struct HomeBoardDeck: View {
    @Environment(PanoStore.self) private var pano
    let previewCount: Int
    let onOpenBoard: (PanoBoard) -> Void

    @State private var expanded = false
    @State private var creating = false
    @State private var editing: PanoBoard?
    @State private var pressed: String?

    var body: some View {
        let unread = PanoSeen.shared.unread(pano)
        // Boards with news first, then the newest.
        let boards = pano.boards.sorted {
            let a = (unread[$0.id] ?? 0) > 0, b = (unread[$1.id] ?? 0) > 0
            return a != b ? a : $0.updatedAt > $1.updatedAt
        }
        let colors = PanoColors.colors(for: boards.map(\.id), chosen: boards.reduce(into: [:]) { $0[$1.id] = $1.color })
        let shown = expanded ? boards : Array(boards.prefix(max(previewCount, 1)))
        return VStack(spacing: 0) {
            if boards.isEmpty {
                PanoDeckCard(title: t("Yeni pano"), color: Color(argb: 0xFF1C1C20), ink: .white,
                             pills: [t("Kart, not, fotoğraf")], last: true)
                    .contentShape(Rectangle())
                    .onTapGesture { creating = true }
                    .accessibilityAddTraits(.isButton)
            } else {
                VStack(spacing: -72) {
                    ForEach(Array(shown.enumerated()), id: \.element.id) { index, board in
                        card(board, index: index, count: shown.count,
                             color: colors[board.id] ?? PanoColors.sand, news: unread[board.id] ?? 0)
                    }
                }
            }
            if expanded && !boards.isEmpty {
                Button { creating = true } label: {
                    Text(t("＋  Yeni pano"))
                        .font(RealFont.inter(15))
                        .foregroundStyle(Color(argb: 0xFFCCCCD3))
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .overlay(RoundedRectangle(cornerRadius: 24).strokeBorder(Color(argb: 0xFF35353A), lineWidth: 0.75))
                }
                .padding(.top, 16)
                .padding(.bottom, 8)
            }
            if !boards.isEmpty {
                Button(expanded ? t("Daha az göster") : t("Tüm panolar")) {
                    withAnimation(.spring(duration: 0.4)) { expanded.toggle() }
                }
                .font(RealFont.inter(14, .medium))
                .foregroundStyle(Color(argb: 0xFFB7B7C0))
                .padding(.vertical, 10)
            }
        }
        .padding(.bottom, 12)
        .sheet(isPresented: $creating) {
            PanoCreateSheet(initialColor: boards.count % PanoColors.palette.count) { title, shared, color in
                let board = pano.createBoard(title: title, shared: shared, color: color)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { onOpenBoard(board) }
            }
        }
        .sheet(item: $editing) { board in
            PanoCreateSheet(editing: board,
                            initialColor: PanoColors.palette.firstIndex(of: colors[board.id] ?? PanoColors.sand) ?? 0,
                            blockCount: pano.items(of: board.id).count)
        }
    }

    private func card(_ board: PanoBoard, index: Int, count: Int, color: Color, news: Int) -> some View {
        let last = index == count - 1
        var pills = [t("{0} blok", pano.items(of: board.id).count)]
        // The board's description sits where the owner's name used to be.
        if let d = board.description, !d.isEmpty { pills.append(d) }
        let owner = board.role == "OWNER"
        // Members as overlapping initials, only when the board has more than one person.
        let people = pano.members[board.id] ?? []
        return PanoDeckCard(title: board.title, color: color, ink: deckInk, pills: pills, last: last, news: news,
                            people: people.count > 1 ? people : [])
            .scaleEffect(pressed == board.id ? 0.985 : 1)
            .contentShape(Rectangle())
            .onTapGesture { onOpenBoard(board) }
            .onLongPressGesture(minimumDuration: 0.4, perform: {
                guard owner else { return }
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                editing = board
            }, onPressingChanged: { pressing in
                withAnimation(.spring(duration: 0.2)) { pressed = pressing ? board.id : nil }
            })
            .accessibilityAddTraits(.isButton)
            .accessibilityAction(named: t("Panoyu düzenle")) { if owner { editing = board } }
            .zIndex(Double(index))
            .modifier(StackPin(index: index, count: count))
    }
}

/// One board in the home deck: CategoryTileView's shape on the board's flat colour.
struct PanoDeckCard: View {
    let title: String
    let color: Color
    let ink: Color
    let pills: [String]
    var last = true
    var news = 0
    var people: [String] = []

    private static let faceColors: [UInt32] = [0xFFC4473C, 0xFF2F6FE0, 0xFF2F8F74, 0xFFE8A33A, 0xFF7A52C7]

    /// Up to three faces; with more people the last one reads "+n".
    private var faces: [(label: String, fill: Color)] {
        let more = people.count > 3
        var out = (more ? Array(people.prefix(2)) : people).map { name -> (label: String, fill: Color) in
            let t = name.trimmingCharacters(in: .whitespaces)
            let hash = t.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7fffffff }
            return (t.isEmpty ? "?" : String(t.prefix(1)).uppercased(), Color(argb: Self.faceColors[hash % Self.faceColors.count]))
        }
        if more { out.append(("+\(people.count - 2)", Color(argb: 0xFF2A2A2F))) }
        return out
    }

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                Text(title).font(RealType.categoryTitle).foregroundStyle(ink).lineLimit(1).offset(x: 10)
                Spacer(minLength: 12)
                Text("↗").font(RealFont.inter(28)).foregroundStyle(ink)
            }
            Spacer().frame(height: 14)
            HStack(spacing: 8) {
                if news > 0 {
                    Text(t("{0} yeni", news))
                        .font(RealFont.inter(12, .bold)).foregroundStyle(.white).lineLimit(1)
                        .padding(.horizontal, 9).padding(.vertical, 3)
                        .background(Capsule().fill(panoNewsColor))
                }
                ForEach(Array(pills.enumerated()), id: \.offset) { _, pill in
                    CategoryPill(text: pill, foreground: ink).frame(maxWidth: 150, alignment: .leading)
                }
                Spacer()
                if !people.isEmpty {
                    HStack(spacing: -7) {
                        ForEach(Array(faces.enumerated()), id: \.offset) { i, face in
                            Text(face.label).font(RealFont.inter(10, .bold)).foregroundStyle(.white).lineLimit(1)
                                .frame(width: 24, height: 24)
                                .background(Circle().fill(face.fill))
                                .overlay(Circle().strokeBorder(.white.opacity(0.85), lineWidth: 1.5))
                                .zIndex(Double(i))
                        }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(people.joined(separator: ", "))
                }
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, 16)
        .padding(.bottom, 46 + (last ? 0 : 40))
        .frame(maxWidth: .infinity, minHeight: 108, alignment: .topLeading)
        .background(shape.fill(color))
        .overlay(shape.strokeBorder(.white.opacity(0.2), lineWidth: 0.75))
        .clipShape(shape)
    }
}

/// Two dots under the home deck: categories, boards. The boards dot turns the news colour when a board has
/// something new, which is the hint to swipe.
struct HomeDeckDots: View {
    let page: Int
    let boardsNews: Bool

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<2, id: \.self) { i in
                Capsule()
                    .fill(i == 1 && boardsNews ? panoNewsColor : i == page ? Color(argb: 0xFFD8D8E0) : Color(argb: 0xFF3A3A42))
                    .frame(width: i == page ? 18 : 6, height: 6)
            }
        }
        .animation(.spring(duration: 0.3), value: page)
        .frame(maxWidth: .infinity)
        .padding(.top, 10)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(boardsNews ? t("Panolarda yeni bir şey var") : (page == 0 ? t("Kategoriler") : t("Panolar")))
    }
}

/// "Yeni pano" from home, and the owner's "Panoyu düzenle": the same sheet as "Yeni Kategori" (Vazgeç / Oluştur,
/// live card preview, AD, RENK; AÇIKLAMA when editing, PAYLAŞIM when creating).
struct PanoCreateSheet: View {
    @Environment(PanoStore.self) private var pano
    @Environment(\.dismiss) private var dismiss
    var editing: PanoBoard? = nil
    var initialColor = 0
    var blockCount = 0
    var onCreate: ((String, Bool, Int) -> Void)? = nil

    @State private var name = ""
    @State private var description = ""
    @State private var colorIndex = 0
    @State private var shared = false
    @State private var saving = false
    @State private var error: String?
    @FocusState private var nameFocused: Bool

    private static let blue = Color(argb: 0xFF2C5696)
    private var edit: Bool { editing != nil }
    private var canSave: Bool { !name.trimmingCharacters(in: .whitespaces).isEmpty && !saving }

    var body: some View {
        VStack(spacing: 0) {
            header
            PanoDeckCard(title: name.isEmpty ? t("Yeni pano") : name, color: PanoColors.palette[colorIndex], ink: deckInk,
                         pills: previewPills)
                .shadow(color: .black.opacity(0.5), radius: 18, y: 10)
                .padding(.horizontal, 16).padding(.vertical, 8)
                .animation(.easeOut(duration: 0.2), value: colorIndex)
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    section(t("AD")) {
                        HStack {
                            TextField("", text: $name, prompt: Text(t("Pano adı")).foregroundStyle(Color(argb: 0xFF6F6F78)))
                                .font(RealFont.inter(16, .medium)).foregroundStyle(.white).tint(RealColors.accentBlue)
                                .focused($nameFocused)
                                .onChange(of: name) { _, v in if v.count > 24 { name = String(v.prefix(24)) } }
                            Text("\(name.count)/24").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF6F6F78))
                        }
                        .padding(.horizontal, 16).frame(minHeight: 52)
                    }
                    section(t("RENK")) {
                        HStack {
                            ForEach(PanoColors.palette.indices, id: \.self) { i in
                                swatch(i)
                                if i < PanoColors.palette.count - 1 { Spacer(minLength: 0) }
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 14)
                    }
                    if edit {
                        section(t("AÇIKLAMA"), footer: error ?? t("Kartta blok sayısının yanında görünür; panodaki herkes görür.")) {
                            HStack {
                                TextField("", text: $description, prompt: Text(t("Kısa bir açıklama")).foregroundStyle(Color(argb: 0xFF6F6F78)))
                                    .font(RealFont.inter(16)).foregroundStyle(.white).tint(RealColors.accentBlue)
                                    .onChange(of: description) { _, v in if v.count > 40 { description = String(v.prefix(40)) } }
                                Text("\(description.count)/40").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF6F6F78))
                            }
                            .padding(.horizontal, 16).frame(minHeight: 52)
                        }
                    } else {
                        section(t("PAYLAŞIM"), footer: t("Açıkken pano bulutta durur ve kişi davet edebilirsin.")) {
                            Button { withAnimation(.easeOut(duration: 0.18)) { shared.toggle() } } label: {
                                HStack(spacing: 12) {
                                    Text(t("Başkalarıyla paylaş")).font(RealFont.inter(16)).foregroundStyle(.white)
                                    Spacer()
                                    RealSwitch(isOn: shared)
                                }
                                .padding(.horizontal, 16).padding(.vertical, 14)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 24)
            }
        }
        .presentationBackground(.ultraThinMaterial)
        .presentationCornerRadius(32)
        .presentationDetents([.large])
        .onAppear {
            colorIndex = min(max(initialColor, 0), PanoColors.palette.count - 1)
            if let editing {
                name = editing.title
                description = editing.description ?? ""
            } else {
                nameFocused = true
            }
        }
    }

    private var previewPills: [String] {
        var pills = [t("{0} blok", blockCount)]
        let d = description.trimmingCharacters(in: .whitespaces)
        if edit { if !d.isEmpty { pills.append(d) } } else if shared { pills.append(t("Paylaşılan")) }
        return pills
    }

    private var header: some View {
        HStack {
            Button(t("Vazgeç")) { dismiss() }.font(RealFont.inter(17)).foregroundStyle(RealColors.accentBlue)
            Spacer()
            Text(edit ? t("Panoyu düzenle") : t("Yeni Pano")).font(RealFont.inter(17, .semibold)).foregroundStyle(.white).lineLimit(1)
            Spacer()
            Button(action: save) {
                Text(edit ? t("Kaydet") : t("Oluştur")).font(RealFont.inter(16, .semibold))
                    .foregroundStyle(canSave ? Color.white : Color.white.opacity(0.4))
                    .padding(.horizontal, 16).frame(height: 40)
                    .background(Capsule().fill(canSave ? Self.blue : Self.blue.opacity(0.35)))
            }
            .disabled(!canSave)
        }
        .padding(.horizontal, 18).padding(.top, 18).padding(.bottom, 14)
    }

    private func swatch(_ i: Int) -> some View {
        let on = i == colorIndex
        return Button { colorIndex = i } label: {
            Circle().fill(PanoColors.palette[i])
                .padding(on ? 4 : 0)
                .overlay(Circle().strokeBorder(on ? Color.white : .clear, lineWidth: 2.5))
                .frame(width: 38, height: 38)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(t("Renk {0}", "\(i + 1)"))
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func save() {
        let title = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }
        if let editing {
            // Editing waits for the server; the sheet closes once it is saved.
            saving = true
            error = nil
            Task { @MainActor in
                do {
                    try await pano.editBoard(editing.id, title: title, description: description, color: colorIndex)
                    dismiss()
                } catch {
                    self.error = t("Kaydedilemedi. Bağlantını kontrol edip yeniden dene.")
                }
                saving = false
            }
        } else {
            let s = shared, c = colorIndex
            dismiss()
            onCreate?(title, s, c)
        }
    }

    private func section<Content: View>(_ title: String, footer: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(RealFont.inter(13, .semibold)).kerning(0.6).foregroundStyle(Color(argb: 0xFF9A9AA2)).padding(.leading, 16)
            content()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(argb: 0xFF16161A)))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color.white.opacity(0.07), lineWidth: 0.8))
            if let footer { Text(footer).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF7C7C85)).padding(.horizontal, 16) }
        }
    }
}
