import SwiftUI
import PhotosUI
import UIKit

/// Board canvas (PanoBoardScreen.kt + BoardCanvas.kt): world-anchored dot grid, pan + pinch zoom, blocks drawn
/// at their transforms. Pencil = edit mode: drag blocks, delete the selected one, and the bottom dock with
/// "+ Add", zoom − / % / + and Reset.
struct PanoBoardScreen: View {
    @Environment(PanoStore.self) private var pano
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let boardId: String

    @State private var viewport = BoardViewport()
    @State private var gestureStart: BoardViewport?
    @State private var editing = false
    @State private var selected: String?
    @State private var adding = false
    @State private var dragging: [String: CGSize] = [:]
    @State private var revealed = false
    @State private var photoItem: PhotosPickerItem?
    @State private var showPhotoPicker = false
    @State private var editingText: PanoItem?

    private var board: PanoBoard? { pano.boards.first { $0.id == boardId } }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                PanoDotGrid(viewport: viewport).ignoresSafeArea()
                ForEach(pano.items(of: boardId)) { item in
                    block(item)
                }
            }
            .contentShape(Rectangle())
            .gesture(panGesture.simultaneously(with: zoomGesture))
            .onTapGesture { selected = nil }
            .opacity(revealed ? 1 : 0)
            .overlay(alignment: .topTrailing) { topControls.padding(.top, 10).padding(.trailing, 14) }
            .overlay(alignment: .topLeading) {
                Button { dismiss() } label: {
                    RealSymbol("chevron.left", size: 19.5).foregroundStyle(.white)
                        .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
                }
                .padding(12)
                .accessibilityLabel("Panolara dön")
            }
            .overlay(alignment: .bottom) { if editing { editDock(size: geo.size).padding(.bottom, 14).transition(.move(edge: .bottom).combined(with: .opacity)) } }
            .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.22), value: editing)
            .sheet(isPresented: $adding) { PanoAddSheet { type, reminderId in add(type, reminderId: reminderId, size: geo.size) } }
        }
        .background(Color.black.ignoresSafeArea())
        .onAppear {
            viewport = pano.viewports[boardId] ?? BoardViewport()
            withAnimation(.easeOut(duration: 0.44)) { revealed = true }
        }
        .onDisappear { pano.viewports[boardId] = viewport; pano.save() }
        .photosPicker(isPresented: $showPhotoPicker, selection: $photoItem, matching: .images)
        .onChange(of: photoItem) { _, item in importPhoto(item) }
        .sheet(item: $editingText) { item in PanoTextEditor(item: item) }
    }

    // MARK: Gestures

    private var panGesture: some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { v in
                let start = gestureStart ?? viewport
                if gestureStart == nil { gestureStart = viewport }
                viewport.x = start.x + v.translation.width / start.zoom
                viewport.y = start.y + v.translation.height / start.zoom
            }
            .onEnded { _ in gestureStart = nil }
    }

    private var zoomGesture: some Gesture {
        MagnifyGesture()
            .onChanged { v in
                let start = gestureStart ?? viewport
                if gestureStart == nil { gestureStart = viewport }
                viewport.zoom = min(max(start.zoom * v.magnification, BoardViewport.minZoom), BoardViewport.maxZoom)
            }
            .onEnded { _ in gestureStart = nil }
    }

    // MARK: Blocks

    private func screenPoint(_ t: BoardTransform) -> CGPoint {
        CGPoint(x: (t.x + viewport.x) * viewport.zoom, y: (t.y + viewport.y) * viewport.zoom)
    }

    @ViewBuilder
    private func block(_ item: PanoItem) -> some View {
        let t = item.transform
        let origin = screenPoint(t)
        let offset = dragging[item.id] ?? .zero
        let isSelected = selected == item.id
        PanoBlockView(item: item, reminder: item.reminderId.flatMap { store.reminder($0) },
                      onToggle: { entryId in toggleChecklist(item, entryId) })
            .frame(width: t.width, height: t.height)
            .overlay {
                if isSelected && editing {
                    RoundedRectangle(cornerRadius: 22).strokeBorder(RealColors.accentBlue, lineWidth: 2)
                }
            }
            .overlay(alignment: .topTrailing) {
                if isSelected && editing {
                    Button { pano.remove(item); selected = nil } label: {
                        RealSymbol("xmark", size: 12.6).foregroundStyle(.white)
                            .frame(width: 26, height: 26).background(Circle().fill(PanoColors.danger))
                    }
                    .offset(x: 10, y: -10)
                }
            }
            .rotationEffect(.degrees(t.rotation))
            .scaleEffect(viewport.zoom, anchor: .topLeading)
            .position(x: origin.x + t.width * viewport.zoom / 2 + offset.width, y: origin.y + t.height * viewport.zoom / 2 + offset.height)
            .zIndex(Double(t.z))
            .onTapGesture {
                if editing {
                    selected = item.id
                    pano.bringToFront(item)
                } else if item.type == .text {
                    editingText = item
                }
            }
            .gesture(blockDrag(item), including: editing ? .all : .subviews)
    }

    private func blockDrag(_ item: PanoItem) -> some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { v in
                selected = item.id
                dragging[item.id] = v.translation
            }
            .onEnded { v in
                var moved = item
                moved.transform.x += v.translation.width / viewport.zoom
                moved.transform.y += v.translation.height / viewport.zoom
                dragging[item.id] = nil
                pano.update(moved)
            }
    }

    private func toggleChecklist(_ item: PanoItem, _ entryId: String) {
        var it = item
        it.checklist = it.checklist.map { e in var c = e; if c.id == entryId { c.done.toggle() }; return c }
        pano.update(it)
    }

    // MARK: Chrome

    private var topControls: some View {
        HStack(spacing: 8) {
            if editing {
                Button { editing = false; selected = nil } label: {
                    Label("Done", systemImage: "checkmark").font(RealFont.inter(14, .semibold)).foregroundStyle(.black)
                        .padding(.horizontal, 14).frame(height: 40).background(Capsule().fill(RealColors.accentBlue))
                }
            } else {
                Button { editing = true } label: {
                    RealSymbol("pencil", size: 18.4).foregroundStyle(.white)
                        .frame(width: 40, height: 40).liquidGlass(Circle(), role: .chrome)
                }
                .accessibilityLabel("Edit")
                HStack(spacing: 6) {
                    Text("ME").font(.system(size: 10, weight: .semibold)).foregroundStyle(.white)
                        .frame(width: 28, height: 28).background(Circle().fill(Color(argb: 0xFF0C0C0E)))
                    RealSymbol("plus", size: 12.6).foregroundStyle(.white.opacity(0.8))
                }
                .padding(.horizontal, 8).frame(height: 40)
                .liquidGlass(Capsule(), role: .chrome)
                .accessibilityLabel("People")
            }
        }
        .animation(.easeOut(duration: 0.18), value: editing)
    }

    private func editDock(size: CGSize) -> some View {
        HStack(spacing: 6) {
            Button { adding = true } label: {
                HStack(spacing: 5) {
                    Text("+").font(.system(size: 17, weight: .semibold))
                    Text("Add").font(.system(size: 14, weight: .semibold))
                }
                .foregroundStyle(.white).padding(.horizontal, 14).frame(height: 34)
                .background(Capsule().fill(.white.opacity(0.16)))
                .overlay(Capsule().strokeBorder(.white.opacity(0.22), lineWidth: 1))
            }
            Rectangle().fill(.white.opacity(0.12)).frame(width: 1, height: 20)
            dockButton("−", "Zoom out") { zoom(by: 1 / 1.2, size: size) }
            Text("\(Int(viewport.zoom * 100))%").font(.system(size: 12, weight: .medium)).foregroundStyle(Color(argb: 0xFFB4B4BC)).frame(minWidth: 40)
            dockButton("+", "Zoom in") { zoom(by: 1.2, size: size) }
            Rectangle().fill(.white.opacity(0.12)).frame(width: 1, height: 20)
            Button { withAnimation(.spring(duration: 0.35)) { viewport = BoardViewport() } } label: {
                Text("Reset").font(.system(size: 12)).foregroundStyle(Color(argb: 0xFFB4B4BC)).padding(.horizontal, 10).frame(minHeight: 34)
            }
            .opacity(viewport == BoardViewport() ? 0.45 : 1)
        }
        .padding(.horizontal, 6).padding(.vertical, 4)
        .liquidGlass(RoundedRectangle(cornerRadius: 24, style: .continuous), role: .chrome)
    }

    private func dockButton(_ label: String, _ description: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.system(size: 18)).foregroundStyle(.white).frame(width: 34, height: 34).background(Circle().fill(.white.opacity(0.06)))
        }
        .accessibilityLabel(description)
    }

    private func zoom(by factor: Double, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let worldX = center.x / viewport.zoom - viewport.x
        let worldY = center.y / viewport.zoom - viewport.y
        let newZoom = min(max(viewport.zoom * factor, BoardViewport.minZoom), BoardViewport.maxZoom)
        withAnimation(.spring(duration: 0.3)) {
            viewport.zoom = newZoom
            viewport.x = center.x / newZoom - worldX
            viewport.y = center.y / newZoom - worldY
        }
    }

    // MARK: Adding

    private func placement(size: CGSize, width: Double = 280, height: Double = 180) -> BoardTransform {
        let cx = size.width / 2 / viewport.zoom - viewport.x
        let cy = size.height / 2 / viewport.zoom - viewport.y
        return BoardTransform(x: cx - width / 2, y: cy - height / 2, width: width, height: height)
    }

    private func add(_ type: BoardBlockType, reminderId: Int64?, size: CGSize) {
        adding = false
        switch type {
        case .photo:
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { showPhotoPicker = true }
        case .text:
            let item = pano.add(PanoItem(boardId: boardId, type: .text, transform: placement(size: size, width: 240, height: 140), text: ""))
            selected = item.id
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { editingText = item }
        case .card:
            let item = pano.add(PanoItem(boardId: boardId, type: .card, transform: placement(size: size, width: 300, height: 200), reminderId: reminderId))
            selected = item.id
        case .checklist:
            let entries = [PanoChecklistEntry(text: "Madde 1"), PanoChecklistEntry(text: "Madde 2")]
            let item = pano.add(PanoItem(boardId: boardId, type: .checklist, transform: placement(size: size, width: 260, height: 200), checklist: entries))
            selected = item.id
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { editingText = item }
        case .stack:
            let cards = [CardStackCard(title: "Kart 1", body: "", colorArgb: Int(Int32(bitPattern: 0xFF4558C8))),
                         CardStackCard(title: "Kart 2", body: "", colorArgb: Int(Int32(bitPattern: 0xFFDAB7F3)))]
            let item = pano.add(PanoItem(boardId: boardId, type: .stack, transform: placement(size: size, width: 280, height: 320), stack: cards))
            selected = item.id
        }
        editing = true
    }

    private func importPhoto(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            guard let data = try? await item.loadTransferable(type: Data.self), let a = AttachmentStorage.save(data) else { return }
            let image = AttachmentStorage.image(a.localPath)
            let ratio = image.map { $0.size.height / max($0.size.width, 1) } ?? 1
            await MainActor.run {
                let w = 260.0
                let screen = UIScreen.main.bounds.size
                let t = placement(size: screen, width: w, height: min(max(w * ratio, 100), 800))
                let added = pano.add(PanoItem(boardId: boardId, type: .photo, transform: t, photoPath: a.localPath))
                selected = added.id
                photoItem = nil
            }
        }
    }
}

/// World-anchored dot grid that keeps its density bounded at any zoom (BoardBackground).
struct PanoDotGrid: View {
    let viewport: BoardViewport
    var body: some View {
        Canvas { ctx, size in
            var spacing = 32 * viewport.zoom
            while spacing < 14 { spacing *= 2 }
            let ox = (viewport.x * viewport.zoom).truncatingRemainder(dividingBy: spacing)
            let oy = (viewport.y * viewport.zoom).truncatingRemainder(dividingBy: spacing)
            var x = ox < 0 ? ox + spacing : ox
            let color = Color.white.opacity(0.16)
            while x < size.width {
                var y = oy < 0 ? oy + spacing : oy
                while y < size.height {
                    ctx.fill(Path(ellipseIn: CGRect(x: x - 0.9, y: y - 0.9, width: 1.8, height: 1.8)), with: .color(color))
                    y += spacing
                }
                x += spacing
            }
        }
        .background(Color.black)
    }
}

/// One block on the board: text note, REAL card, checklist, photo or card stack.
struct PanoBlockView: View {
    let item: PanoItem
    let reminder: ReminderItem?
    let onToggle: (String) -> Void

    var body: some View {
        switch item.type {
        case .text:
            Text(item.text.isEmpty ? "Text" : item.text)
                .font(RealFont.inter(17)).foregroundStyle(item.text.isEmpty ? Color.white.opacity(0.4) : Color.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(Color(argb: 0xFF1A1A1E)))
        case .card:
            if let reminder {
                ReminderCardView(reminder: reminder, showCategoryStripes: false)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else {
                Text("REAL card").font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.6))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: 22).fill(RealColors.cardSurface))
            }
        case .checklist:
            VStack(alignment: .leading, spacing: 8) {
                Text("Checklist").font(RealFont.inter(13, .medium)).foregroundStyle(Color(argb: 0xFFB7B7C0))
                ForEach(item.checklist) { e in
                    Button { onToggle(e.id) } label: {
                        HStack(spacing: 10) {
                            Image(systemName: e.done ? "checkmark.circle.fill" : "circle").font(.system(size: 17))
                                .foregroundStyle(e.done ? RealColors.accentBlue : Color(argb: 0xFF5E5E68))
                            Text(e.text).font(RealFont.inter(15)).foregroundStyle(e.done ? Color(argb: 0xFF6E6E77) : .white)
                                .strikethrough(e.done)
                        }
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .cardContentChrome(corners: 22)
        case .photo:
            Group {
                if let path = item.photoPath, let img = AttachmentStorage.image(path) {
                    Image(uiImage: img).resizable().scaledToFill()
                } else { Color(argb: 0xFF1A1A1E) }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        case .stack:
            CardStackDeck(stack: CardStackBlock(cards: item.stack))
                .scaleEffect(0.7, anchor: .top)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
    }
}

/// "Add to Pano" sheet: Create (Text, Checklist, Card Stack, Photo) and REAL cards.
struct PanoAddSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let onAdd: (BoardBlockType, Int64?) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("Create") {
                    row("Text", "textformat") { onAdd(.text, nil) }
                    row("Checklist", "checklist") { onAdd(.checklist, nil) }
                    row("Card Stack", "rectangle.stack") { onAdd(.stack, nil) }
                    row("Photo", "photo") { onAdd(.photo, nil) }
                }
                Section("REAL card") {
                    if store.reminders.isEmpty {
                        Text("No local cards available.").foregroundStyle(.secondary)
                    }
                    ForEach(store.reminders.sorted { $0.createdAt > $1.createdAt }.prefix(40)) { r in
                        Button { onAdd(.card, r.id) } label: {
                            Text(r.text.isEmpty ? "Boş not" : r.text).foregroundStyle(.white).lineLimit(1)
                        }
                    }
                }
                Section("More") {
                    ForEach(["Location", "Music", "Movie / TV", "Mood", "Voice", "Link / snippet", "File / Attachment", "GIF"], id: \.self) { name in
                        HStack {
                            Text(name).foregroundStyle(.secondary)
                            Spacer()
                            Text("Yakında").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Add to Pano")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Kapat") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(.ultraThinMaterial)
    }

    private func row(_ title: String, _ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) { Label(title, systemImage: symbol).foregroundStyle(.white) }
    }
}

/// Edit a text note or a checklist block.
struct PanoTextEditor: View {
    @Environment(PanoStore.self) private var pano
    @Environment(\.dismiss) private var dismiss
    @State var item: PanoItem
    @State private var newEntry = ""

    var body: some View {
        NavigationStack {
            Form {
                if item.type == .checklist {
                    ForEach($item.checklist) { $entry in
                        HStack {
                            Button { entry.done.toggle() } label: { Image(systemName: entry.done ? "checkmark.circle.fill" : "circle") }
                            TextField("Madde", text: $entry.text)
                        }
                    }
                    .onDelete { item.checklist.remove(atOffsets: $0) }
                    TextField("Madde ekle", text: $newEntry).onSubmit {
                        let t = newEntry.trimmingCharacters(in: .whitespaces)
                        if !t.isEmpty { item.checklist.append(PanoChecklistEntry(text: t)) }
                        newEntry = ""
                    }
                } else {
                    TextField("Text", text: $item.text, axis: .vertical).lineLimit(3...12)
                }
            }
            .navigationTitle(item.type == .checklist ? "Checklist" : "Text")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Kaydet") { pano.update(item); dismiss() } }
            }
        }
        .presentationDetents([.medium])
    }
}
