import SwiftUI
import PhotosUI
import UIKit

/// Pano palette (PanoGalleryUi.kt): flat "poster" cards with big black type.
enum PanoColors {
    static let sand = Color(argb: 0xFFE6D9C3)
    static let sage = Color(argb: 0xFFB2BFA6)
    static let clay = Color(argb: 0xFFD29B83)
    static let mist = Color(argb: 0xFFA9B9C9)
    static let lilac = Color(argb: 0xFFB9AEC9)
    static let ivory = Color(argb: 0xFFEFEAE0)
    static let accentWarm = Color(argb: 0xFFC4704F)
    static let ink = Color(argb: 0xFF151517)
    static let inkDark = Color(argb: 0xFF1E1E20)
    static let danger = Color(argb: 0xFFE5484D)
    static let muted = Color(argb: 0xFF8C8C95)
    static let palette = [sand, sage, clay, mist, lilac, ivory]

    /// Stable colour per board; neighbours never share one.
    static func colors(for ids: [String]) -> [String: Color] {
        var out: [String: Color] = [:]
        var prev = -1
        for id in ids {
            var i = abs(id.hashValue) % palette.count
            let stable = id.unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7fffffff }
            i = stable % palette.count
            if i == prev { i = (i + 1) % palette.count }
            out[id] = palette[i]
            prev = i
        }
        return out
    }
}

private func panoBig(_ text: String, _ size: CGFloat, color: Color = PanoColors.ink, lines: Int = 3, strike: Bool = false) -> some View {
    Text(text)
        .font(.system(size: size))
        .kerning(-0.025 * size)
        .lineSpacing(-size * 0.08)
        .foregroundStyle(color)
        .strikethrough(strike, color: color)
        .lineLimit(lines)
}

/// Pano gallery: header (back · +, WEEKDAY DAY, PANO•), My boards / Shared with me poster cards.
struct PanoScreen: View {
    @Environment(PanoStore.self) private var pano
    @Environment(\.dismiss) private var dismiss
    @State private var revealed = false
    @State private var creating = false
    @State private var peek: PanoBoard?
    @State private var deleting: PanoBoard?
    @State private var openBoard: PanoBoard?

    var body: some View {
        let mine = pano.boards.filter { $0.role == "OWNER" }
        let others = pano.boards.filter { $0.role != "OWNER" }
        let colors = PanoColors.colors(for: (mine + others).map(\.id))
        VStack(spacing: 0) {
            header.yasamEnter(revealed, delay: 0, y: 0)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if pano.boards.isEmpty { emptyState.yasamEnter(revealed, delay: 0.08, y: 16) }
                    if !mine.isEmpty { sectionLabel("My boards").yasamEnter(revealed, delay: 0.12, y: 0) }
                    ForEach(Array(mine.enumerated()), id: \.element.id) { index, board in
                        card(board, color: colors[board.id] ?? PanoColors.sand)
                            .yasamEnter(revealed, delay: 0.2 + Double(min(index, 5)) * 0.055, y: 14, scale: 0.96)
                    }
                    if !others.isEmpty { sectionLabel("Shared with me").yasamEnter(revealed, delay: 0.18, y: 0) }
                    ForEach(Array(others.enumerated()), id: \.element.id) { index, board in
                        card(board, color: colors[board.id] ?? PanoColors.sand)
                            .yasamEnter(revealed, delay: 0.28 + Double(min(index, 5)) * 0.055, y: 14, scale: 0.96)
                    }
                }
                .padding(.horizontal, 16).padding(.top, 18).padding(.bottom, 32)
            }
        }
        .background(Color.black.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { withAnimation { revealed = true } }
        .fullScreenCover(item: $peek) { b in
            PanoBoardPeek(board: b, color: colors[b.id] ?? PanoColors.sand,
                          onOpen: { peek = nil; DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { openBoard = b } },
                          onRemove: { peek = nil; deleting = b },
                          onDismiss: { peek = nil })
        }
        .fullScreenCover(isPresented: $creating) {
            PanoNewBoardOverlay(onDismiss: { creating = false }) { title, shared in
                creating = false
                let board = pano.createBoard(title: title, shared: shared)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { openBoard = board }
            }
            .presentationBackground(.clear)
        }
        .fullScreenCover(item: $openBoard) { b in PanoBoardScreen(boardId: b.id) }
        .alert("Delete board?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("Delete", role: .destructive) { if let d = deleting { pano.deleteBoard(d.id) }; deleting = nil }
            Button("Cancel", role: .cancel) { deleting = nil }
        } message: { Text("This removes the board. Original REAL cards are kept.") }
    }

    private var header: some View {
        let today = Date()
        let cal = Calendar.real
        let day = cal.isoWeekday(of: today).full.uppercased(with: Locale(identifier: "tr_TR"))
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button { dismiss() } label: {
                    RealSymbol("chevron.left", size: 21.8).foregroundStyle(.white)
                        .frame(width: 44, height: 44).liquidGlass(Circle(), role: .chrome)
                }
                .accessibilityLabel("Geri")
                Spacer()
                Button { withoutAnimation { creating = true } } label: {
                    RealSymbol("plus", size: 29.9).foregroundStyle(.white).frame(width: 48, height: 48)
                }
                .accessibilityLabel("New board")
            }
            Text("\(day) \(cal.component(.day, from: today))").font(.system(size: 13)).kerning(0.4).foregroundStyle(.white).padding(.top, 14)
            HStack(alignment: .bottom, spacing: 5) {
                panoBig("PANO", 56, color: .white, lines: 1)
                Circle().fill(PanoColors.accentWarm).frame(width: 8, height: 8).padding(.bottom, 15)
            }
        }
        .padding(.horizontal, 20).padding(.top, 8)
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("A PLACE FOR\nYOUR IDEAS").font(.system(size: 34)).foregroundStyle(.white)
            Text("Arrange cards, notes and photos. Build alone or invite people.").foregroundStyle(.gray)
            Button { withoutAnimation { creating = true } } label: {
                Text("+ NEW BOARD").foregroundStyle(.black).padding(.horizontal, 22).padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 24).fill(PanoColors.ivory))
            }
            .padding(.top, 6)
        }
        .padding(.vertical, 40).padding(.horizontal, 6)
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased()).font(.system(size: 11, weight: .semibold)).kerning(1).foregroundStyle(PanoColors.muted)
            .padding(.leading, 6).padding(.top, 10)
    }

    private func card(_ board: PanoBoard, color: Color) -> some View {
        let d = board.updatedAt.date
        let cal = Calendar.real
        let title = board.title.uppercased(with: Locale(identifier: "tr_TR"))
        return Button { openBoard = board } label: {
            HStack(alignment: .top, spacing: 14) {
                VStack(spacing: 0) {
                    Text("\(cal.component(.month, from: d))").font(.system(size: 22)).foregroundStyle(PanoColors.ink)
                    Text(String(format: "%02d", cal.component(.day, from: d))).font(.system(size: 11)).foregroundStyle(PanoColors.ink.opacity(0.65))
                    Rectangle().fill(PanoColors.ink.opacity(0.45)).frame(width: 1, height: 16).padding(.top, 6)
                }
                .frame(width: 30).padding(.top, 4)
                VStack(alignment: .leading, spacing: 18) {
                    panoBig(title, title.count > 14 ? 30 : 40)
                    Text("ME").font(.system(size: 11, weight: .semibold)).kerning(0.3).foregroundStyle(PanoColors.ink)
                }
                Spacer(minLength: 0)
            }
            .padding(.leading, 16).padding(.trailing, 18).padding(.top, 18).padding(.bottom, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(color))
        }
        .buttonStyle(PressScaleStyle(scale: 0.965))
        .simultaneousGesture(LongPressGesture(minimumDuration: 0.45).onEnded { _ in
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            peek = board
        })
    }
}

/// Long press: the board colour fills the screen; Open / Delete / People cards slide up one after another.
struct PanoBoardPeek: View {
    let board: PanoBoard
    let color: Color
    let onOpen: () -> Void
    let onRemove: () -> Void
    let onDismiss: () -> Void
    @State private var shown = [false, false, false]
    @State private var header = false

    var body: some View {
        let accent = color == PanoColors.sand ? PanoColors.sage : PanoColors.sand
        let title = board.title.uppercased(with: Locale(identifier: "tr_TR"))
        ZStack(alignment: .bottom) {
            color.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Button(action: onDismiss) {
                        RealSymbol("chevron.left", size: 21.8).foregroundStyle(PanoColors.ink).frame(width: 44, height: 44)
                    }
                    Spacer()
                    Text("1 PERSON").font(.system(size: 13, weight: .medium)).foregroundStyle(PanoColors.ink)
                        .padding(.horizontal, 16).padding(.vertical, 6)
                        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(PanoColors.ink, lineWidth: 1.6))
                }
                panoBig(title, title.count > 10 ? 54 : 72).padding(.top, 12).opacity(header ? 1 : 0)
                HStack(spacing: 14) {
                    Text(board.shared ? "SHARED" : "PRIVATE").font(.system(size: 12, weight: .semibold)).kerning(0.4).foregroundStyle(PanoColors.ink)
                    Text("OWNER").font(.system(size: 12)).kerning(0.4).foregroundStyle(PanoColors.ink.opacity(0.45))
                }
                .padding(.top, 10)
                Spacer()
            }
            .padding(.horizontal, 20).padding(.vertical, 12)
            ZStack(alignment: .top) {
                stackCard(top: 0, height: 230, bg: .white, index: 0) { rowHead("OPEN BOARD", ink: PanoColors.ink, strike: false, action: onOpen) { EmptyView() } }
                stackCard(top: 112, height: 230, bg: PanoColors.inkDark, index: 1) {
                    rowHead("DELETE BOARD", ink: Color(argb: 0xFF8A8A90), strike: true, action: onRemove) {
                        RealSymbol("xmark", size: 12.6).foregroundStyle(PanoColors.danger)
                    }
                }
                stackCard(top: 224, height: 256, bg: accent, index: 2) {
                    VStack(spacing: 0) {
                        rowHead("PEOPLE", ink: PanoColors.ink, strike: false, action: {}) { EmptyView() }
                        Spacer()
                        HStack(spacing: 0) {
                            Text("INVITE PERSON").font(.system(size: 16)).foregroundStyle(.white).frame(maxWidth: .infinity)
                            Rectangle().fill(accent).frame(width: 1.5)
                            RealSymbol("plus", size: 23).foregroundStyle(.white).frame(width: 66)
                        }
                        .frame(height: 58)
                        .background(RoundedRectangle(cornerRadius: 29).fill(PanoColors.ink))
                        .padding(.horizontal, 8).padding(.bottom, 30)
                    }
                }
            }
            .frame(height: 480)
            .ignoresSafeArea(edges: .bottom)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.3)) { header = true }
            for i in 0..<3 {
                withAnimation(.spring(response: 0.5, dampingFraction: 0.82).delay(0.12 + Double(i) * 0.09)) { shown[i] = true }
            }
        }
    }

    private func stackCard<C: View>(top: CGFloat, height: CGFloat, bg: Color, index: Int, @ViewBuilder content: () -> C) -> some View {
        content()
            .frame(maxWidth: .infinity, alignment: .top)
            .frame(height: height, alignment: .top)
            .background(UnevenRoundedRectangle(topLeadingRadius: 34, topTrailingRadius: 34, style: .continuous).fill(bg))
            .padding(.horizontal, 10)
            .offset(y: top + (shown[index] ? 0 : 1400))
    }

    private func rowHead<P: View>(_ title: String, ink: Color, strike: Bool, action: @escaping () -> Void, @ViewBuilder pill: () -> P) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    pill().frame(width: 46, height: 27).overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(ink, lineWidth: 2))
                    Spacer()
                    RealSymbol("arrow.up.right", size: 23).foregroundStyle(ink)
                }
                panoBig(title, 34, color: ink, lines: 1, strike: strike)
            }
            .padding(.horizontal, 20).padding(.vertical, 18)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// "+" creator: sand screen that blooms out of the top-right plus; NAME IT field, PRIVATE / SHARED, CREATE BOARD.
struct PanoNewBoardOverlay: View {
    let onDismiss: () -> Void
    let onCreate: (String, Bool) -> Void
    @State private var shown = false
    @State private var title = ""
    @State private var shared = false
    @FocusState private var focused: Bool

    var body: some View {
        let ready = !title.trimmingCharacters(in: .whitespaces).isEmpty
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onDismiss) {
                    RealSymbol("chevron.left", size: 21.8).foregroundStyle(PanoColors.ink).frame(width: 44, height: 44)
                }
                Spacer()
                Text("NEW").font(.system(size: 13, weight: .medium)).foregroundStyle(PanoColors.ink)
                    .padding(.horizontal, 16).padding(.vertical, 6)
                    .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(PanoColors.ink, lineWidth: 1.6))
            }
            panoBig("NEW\nBOARD", 76, lines: 2).padding(.top, 18)
            ZStack(alignment: .leading) {
                if title.isEmpty { panoBig("NAME IT", 36, color: PanoColors.ink.opacity(0.22), lines: 1) }
                TextField("", text: $title)
                    .font(.system(size: 36)).kerning(-0.9).foregroundStyle(PanoColors.ink).tint(PanoColors.ink)
                    .textInputAutocapitalization(.characters)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit { if ready { onCreate(title, shared) } }
                    .onChange(of: title) { _, v in if v.count > 80 { title = String(v.prefix(80)) } }
            }
            .padding(.top, 26)
            Rectangle().fill(PanoColors.ink.opacity(0.85)).frame(height: 1.5).padding(.top, 10)
            HStack(spacing: 10) {
                ForEach([false, true], id: \.self) { value in
                    Button { withAnimation(.easeOut(duration: 0.22)) { shared = value } } label: {
                        Text(value ? "SHARED" : "PRIVATE").font(.system(size: 13, weight: .medium)).kerning(0.4)
                            .foregroundStyle(shared == value ? Color.white : PanoColors.ink)
                            .padding(.horizontal, 20).padding(.vertical, 10)
                            .background(RoundedRectangle(cornerRadius: 22).fill(shared == value ? PanoColors.ink : Color.clear))
                            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(PanoColors.ink, lineWidth: 1.6))
                    }
                }
            }
            .padding(.top, 22)
            Spacer()
            Button { if ready { onCreate(title, shared) } } label: {
                HStack(spacing: 0) {
                    Text("CREATE BOARD").font(.system(size: 16)).foregroundStyle(.white).frame(maxWidth: .infinity)
                    Rectangle().fill(PanoColors.accentWarm).frame(width: 1.5)
                    RealSymbol("plus", size: 23).foregroundStyle(.white)
                        .rotationEffect(.degrees(ready ? 90 : 0)).frame(width: 68)
                        .animation(.spring(response: 0.4, dampingFraction: 0.5), value: ready)
                }
                .frame(height: 60)
                .background(RoundedRectangle(cornerRadius: 30).fill(PanoColors.ink))
                .opacity(ready ? 1 : 0.35)
            }
            .disabled(!ready)
        }
        .padding(.horizontal, 22).padding(.vertical, 12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(PanoColors.sand.ignoresSafeArea())
        .scaleEffect(shown ? 1 : 0.05, anchor: UnitPoint(x: 0.92, y: 0.05))
        .opacity(shown ? 1 : 0)
        .onAppear {
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: 0.46)) { shown = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.38) { focused = true }
        }
    }
}
