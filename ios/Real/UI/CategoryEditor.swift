import SwiftUI

/// Built-in category colour override (long press on a deck tile): same editor, colour only.
struct BuiltInCategoryColorSheet: View {
    let category: ReminderCategory
    var body: some View { CategoryEditorSheet(existing: nil, builtIn: category) }
}

/// "Yeni Kategori" / "Kategoriyi Düzenle" (CreateCategoryDialog.kt): glass sheet with Vazgeç · title · Kaydet pill,
/// live preview tile, AD (20 chars), AÇIKLAMA (30 words), RENK rows + Geçiş bar, İKON grid, GÖRÜNÜM switches, delete.
struct CategoryEditorSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let existing: CustomCategory?
    let builtIn: ReminderCategory?
    let onCreated: ((CustomCategory) -> Void)?

    @State private var name = ""
    @State private var description = ""
    @State private var icon: CategoryIcon = .noIcon
    @State private var start: Int? = 2
    @State private var end: Int?
    @State private var blend = 0.5
    @State private var dark = false
    @State private var hide = false
    @State private var showInCalendar = true
    @State private var confirmDelete = false
    @State private var deleteCards = false

    private static let blue = Color(argb: 0xFF2C5696)

    init(existing: CustomCategory?, builtIn: ReminderCategory? = nil, onCreated: ((CustomCategory) -> Void)? = nil) {
        self.existing = existing
        self.builtIn = builtIn
        self.onCreated = onCreated
    }

    private var isBuiltIn: Bool { builtIn != nil }
    /// Someone else's shared category (full or limited member): the owner's description (read only) and this phone's
    /// "Yalnızca kategori içinde göster". Name, colour, icon and delete stay with the owner.
    private var memberMode: Bool { existing?.isSharedMirror == true }
    private var title: String { isBuiltIn ? (builtIn?.title ?? "") : (existing == nil ? "Yeni Kategori" : "Kategoriyi Düzenle") }
    private var canSave: Bool { isBuiltIn || !name.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        VStack(spacing: 0) {
            header
            preview.padding(.horizontal, 16).padding(.bottom, 6)
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    if memberMode {
                        section("AÇIKLAMA", footer: "Açıklamayı yalnızca kategori sahibi değiştirebilir.") {
                            Text(description.isEmpty ? "Kategori sahibi henüz açıklama yazmadı." : description)
                                .font(RealFont.inter(15))
                                .foregroundStyle(description.isEmpty ? Color(argb: 0xFF6F6F78) : Color(argb: 0xFFF3F3F5))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 16).padding(.vertical, 14)
                        }
                    } else if !isBuiltIn {
                        section("AD") {
                            HStack {
                                TextField("", text: $name, prompt: Text("Kategori adı").foregroundStyle(Color(argb: 0xFF6F6F78)))
                                    .font(RealFont.inter(16, .medium)).foregroundStyle(.white).tint(RealColors.accentBlue)
                                    .onChange(of: name) { _, v in if v.count > 20 { name = String(v.prefix(20)) } }
                                Text("\(name.count)/20").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF6F6F78))
                            }
                            .padding(.horizontal, 16).frame(minHeight: 52)
                        }
                        section("AÇIKLAMA", footer: "Boş bırakırsan açıklama kutusu kaldırılır · \(wordCount)/30 kelime") {
                            TextField("", text: $description, prompt: Text("Bu kategori ne için?").foregroundStyle(Color(argb: 0xFF6F6F78)), axis: .vertical)
                                .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFF3F3F5)).tint(RealColors.accentBlue)
                                .lineLimit(1...3)
                                .onChange(of: description) { _, v in
                                    let limited = Self.limitWords(v)
                                    if limited != v { description = limited }
                                }
                                .padding(.horizontal, 16).padding(.vertical, 14)
                                .frame(minHeight: 76, alignment: .topLeading)
                        }
                    }
                    if !memberMode { section("RENK") {
                        CardColorsPanel(title: nil, start: $start, end: $end, blend: $blend, darkText: $dark,
                                        allowNoneStart: isBuiltIn, showDarkText: false)
                    } }
                    if !isBuiltIn && !memberMode {
                        section("İKON") {
                            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 6), spacing: 10) {
                                ForEach(CategoryIcon.allCases) { option in
                                    iconTile(option)
                                }
                            }
                            .padding(14)
                        }
                    }
                    section("GÖRÜNÜM") {
                        VStack(spacing: 0) {
                            if !isBuiltIn {
                                switchRow("Yalnızca kategori içinde göster", "Kartlar genel listelerde görünmez.", $hide)
                            }
                        }
                    }
                    if existing != nil && !memberMode {
                        // CreateCategoryDialog.kt: the confirmation opens inside the same group, not as a system sheet.
                        VStack(spacing: 8) {
                            if confirmDelete {
                                Text("Bu kategoriyi silmek istiyor musun?")
                                    .font(RealFont.inter(16, .semibold)).foregroundStyle(Color(argb: 0xFFF5F5F8))
                                    .multilineTextAlignment(.center).padding(.horizontal, 16)
                                Button { withAnimation(.easeOut(duration: 0.18)) { deleteCards.toggle() } } label: {
                                    HStack(spacing: 12) {
                                        Text("Kategori içindeki kutucukları da sil").font(RealFont.inter(16)).foregroundStyle(.white)
                                        Spacer()
                                        RealSwitch(isOn: deleteCards, tint: Color(argb: 0xFFFF6B73))
                                    }
                                    .padding(.horizontal, 16).padding(.vertical, 10)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                HStack(spacing: 10) {
                                    Button { withAnimation(.easeOut(duration: 0.18)) { confirmDelete = false } } label: {
                                        Text("Vazgeç").font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFE9E9EF))
                                            .frame(maxWidth: .infinity, minHeight: 46)
                                            .background(Capsule().fill(Color.white.opacity(0.08)))
                                    }
                                    Button { confirmDelete = false; delete(cards: deleteCards) } label: {
                                        Text("Sil").font(RealFont.inter(15, .semibold)).foregroundStyle(Color(argb: 0xFFFF9AA0))
                                            .frame(maxWidth: .infinity, minHeight: 46)
                                            .background(Capsule().fill(Color(argb: 0xFFFF6B73).opacity(0.22)))
                                    }
                                }
                                .buttonStyle(PressScaleStyle(scale: 0.97))
                                .padding(.horizontal, 16)
                            } else {
                                Button { withAnimation(.easeOut(duration: 0.18)) { confirmDelete = true } } label: {
                                    HStack(spacing: 8) {
                                        ActionIcon(.delete, color: Color(argb: 0xFFFF9AA0), size: 16)
                                        Text("Kategoriyi sil").font(RealFont.inter(15, .medium)).foregroundStyle(Color(argb: 0xFFFF9AA0))
                                    }
                                    .frame(maxWidth: .infinity).padding(.vertical, 15)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, confirmDelete ? 16 : 0)
                        .frame(maxWidth: .infinity)
                        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(argb: 0xFF16161A)))
                        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Color.white.opacity(0.07), lineWidth: 0.8))
                    }
                }
                .padding(.horizontal, 16).padding(.top, 6).padding(.bottom, 24)
            }
        }
        .presentationBackground(.ultraThinMaterial)
        .presentationCornerRadius(32)
        .presentationDetents([.large])
        .onAppear(perform: load)
    }

    private var header: some View {
        HStack {
            Button("Vazgeç") { dismiss() }.font(RealFont.inter(17)).foregroundStyle(RealColors.accentBlue)
            Spacer()
            Text(title).font(RealFont.inter(17, .semibold)).foregroundStyle(.white).lineLimit(1)
            Spacer()
            Button(action: save) {
                Text(existing == nil && !isBuiltIn ? "Oluştur" : "Kaydet").font(RealFont.inter(16, .semibold))
                    .foregroundStyle(canSave ? Color.white : Color.white.opacity(0.4))
                    .padding(.horizontal, 16).frame(height: 40)
                    .background(Capsule().fill(canSave ? Self.blue : Self.blue.opacity(0.35)))
            }
            .disabled(!canSave)
        }
        .padding(.horizontal, 18).padding(.top, 18).padding(.bottom, 14)
    }

    private var previewVisual: CategoryVisual {
        if let builtIn {
            return builtIn.visual(override: start == nil && end == nil ? nil
                : CategoryColorOverride(start: start, end: end, useDarkText: dark, gradientBlendFraction: blend))
        }
        return CustomCategory(id: 0, name: name.isEmpty ? "Kategori" : name, iconKey: icon.rawValue, accentStart: start ?? 2,
                              accentEnd: end, useDarkText: dark, gradientBlendFraction: blend).visual
    }

    private var preview: some View {
        CategoryTileView(visual: previewVisual, count: 0, description: description, showCount: false)
            .shadow(color: .black.opacity(0.5), radius: 18, y: 10)
    }

    private func iconTile(_ option: CategoryIcon) -> some View {
        let on = icon == option
        return Button { icon = option } label: {
            Group {
                if option == .noIcon { Image(systemName: "circle.slash").font(.system(size: 19)) }
                else { RealSymbol(option.symbol, size: 21.8) }
            }
            .foregroundStyle(.white)
                .frame(maxWidth: .infinity).frame(height: 50)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(argb: on ? 0xFF2A2A30 : 0xFF1E1E23)))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? Color.white : Color.clear, lineWidth: 2))
        }
        .accessibilityLabel(on ? "İkon: \(option.label), seçili" : "İkon: \(option.label)")
    }

    private var wordCount: Int { description.split(whereSeparator: { $0.isWhitespace }).count }

    private static func limitWords(_ text: String) -> String {
        let words = text.split(whereSeparator: { $0.isWhitespace })
        guard words.count > 30 else { return text }
        return words.prefix(30).joined(separator: " ")
    }

    private var divider: some View { Rectangle().fill(Color.white.opacity(0.07)).frame(height: 1).padding(.leading, 16) }

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

    private func switchRow(_ title: String, _ subtitle: String, _ value: Binding<Bool>) -> some View {
        Button { withAnimation(.easeOut(duration: 0.18)) { value.wrappedValue.toggle() } } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(RealFont.inter(16)).foregroundStyle(.white)
                    Text(subtitle).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8C8C95))
                }
                Spacer()
                RealSwitch(isOn: value.wrappedValue)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func load() {
        if let builtIn {
            let o = store.builtInColors[builtIn]
            start = o?.start
            end = o?.end
            blend = o?.gradientBlendFraction ?? 0.5
            dark = o?.useDarkText ?? builtIn.defaultColors.2
            return
        }
        guard let c = existing else { return }
        name = c.name
        description = c.description
        icon = c.icon
        start = c.accentStart
        end = c.accentEnd
        blend = c.gradientBlendFraction ?? 0.5
        dark = c.useDarkText
        hide = c.hideFromGeneralLists
        showInCalendar = c.isShownInCalendar
    }

    private func delete(cards: Bool) {
        if let c = existing { store.deleteCategory(c.id, deleteReminders: cards) }
        dismiss()
    }

    private func save() {
        if let builtIn {
            store.setBuiltInColor(builtIn, start == nil && end == nil ? nil
                : CategoryColorOverride(start: start, end: end, useDarkText: dark, gradientBlendFraction: end != nil ? blend : nil))
        } else if var c = existing, c.isSharedMirror {
            // Only this phone's switch; the rest is the owner's and the shared sync keeps it in step.
            c.hideFromGeneralLists = hide
            store.updateCategory(c)
        } else if var c = existing {
            c.name = name.trimmingCharacters(in: .whitespaces)
            c.description = description.trimmingCharacters(in: .whitespacesAndNewlines)
            c.iconKey = icon.rawValue
            c.accentStart = start ?? 2
            c.accentEnd = end
            c.gradientBlendFraction = end != nil ? blend : nil
            c.useDarkText = dark
            c.hideFromGeneralLists = hide
            c.showInCalendar = showInCalendar
            store.updateCategory(c)
        } else {
            var created = store.addCategory(name: name, icon: icon, accentStart: start ?? 2, accentEnd: end, hideFromGeneralLists: hide,
                                            useDarkText: dark, blend: end != nil ? blend : nil,
                                            description: description.trimmingCharacters(in: .whitespacesAndNewlines))
            if !showInCalendar {
                created.showInCalendar = false
                store.updateCategory(created)
            }
            onCreated?(created)
        }
        dismiss()
    }
}

/// RealSwitch: green track, white knob.
struct RealSwitch: View {
    let isOn: Bool
    var tint = Color(argb: 0xFF34C759)
    var body: some View {
        Capsule().fill(isOn ? tint : Color.white.opacity(0.16))
            .frame(width: 52, height: 32)
            .overlay(alignment: isOn ? .trailing : .leading) {
                Circle().fill(Color.white).frame(width: 26, height: 26).padding(3).shadow(color: .black.opacity(0.2), radius: 2, y: 1)
            }
            .animation(.easeOut(duration: 0.18), value: isOn)
    }
}

/// Drag to reorder the home deck (CategoryOrderEditorDialog.kt).
/// Kategori düzeni (CategoryOrderEditorDialog.kt): how many categories Home shows in "Daha az göster", the order
/// (drag the handle), the "Tümünü göster" button as its own row, hiding with the eye, hidden ones as chips to add back.
struct CategoryOrderSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @AppStorage("home_category_preview_count_v1") private var savedPreview = 4
    @State private var order: [String] = []
    @State private var hidden: Set<String> = []
    @State private var preview = 4
    @State private var loaded = false

    private var visible: [String] { order.filter { !hidden.contains($0) } }
    private var hiddenList: [String] { order.filter { hidden.contains($0) } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Kategoriler").font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
                Spacer()
                Button {
                    store.setCategoryOrder(order.filter { $0 != ReminderStore.showAllKey }, hidden: hidden)
                    savedPreview = preview
                    dismiss()
                } label: {
                    Text("Tamam").font(RealFont.inter(14, .semibold)).foregroundStyle(.black)
                        .padding(.horizontal, 18).padding(.vertical, 9).background(Capsule().fill(.white))
                }
                .buttonStyle(.plain)
            }
            // How many show on Home before "Tümünü göster".
            HStack {
                Text("Ana sayfada").font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.85))
                Spacer()
                stepButton("−", enabled: preview > 1) { withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) { preview -= 1 } }
                Text("\(preview)").font(RealFont.inter(17, .semibold)).foregroundStyle(.white).frame(minWidth: 26)
                    .contentTransition(.numericText(value: Double(preview)))
                stepButton("+", enabled: preview < max(store.allHomeCategories.count, 1)) { withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) { preview += 1 } }
            }
            .padding(.leading, 16).padding(.trailing, 6).padding(.vertical, 4)
            .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.05)))
            .padding(.top, 14).padding(.bottom, 8)
            List {
                ForEach(visible, id: \.self) { key in
                    row(key)
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
                Text("Gizlenenler").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.45))
                    .padding(.top, 10)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(hiddenList, id: \.self) { key in
                            Button { withAnimation { _ = hidden.remove(key) } } label: {
                                Text("+ \(title(key))").font(RealFont.inter(13, .medium)).foregroundStyle(.white.opacity(0.85))
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(Capsule().fill(.white.opacity(0.07)))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 6)
                }
            }
            Button("Varsayılan") {
                withAnimation {
                    order = store.allHomeCategories.map(\.orderKey) + [ReminderStore.showAllKey]
                    hidden = []
                    preview = 4
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
            order = store.allHomeCategories.map(\.orderKey) + [ReminderStore.showAllKey]
            hidden = store.hiddenHomeKeys
            preview = max(savedPreview, 1)
        }
    }

    private func item(_ key: String) -> OrderedCategory? { store.allHomeCategories.first { $0.orderKey == key } }

    private func title(_ key: String) -> String {
        guard let item = item(key) else { return "Tümünü göster" }
        switch item {
        case .builtIn(let c): return c.visual(override: store.builtInColors[c]).title
        case .custom(let c): return c.name
        }
    }

    private func row(_ key: String) -> some View {
        let item = item(key)
        let visual: CategoryVisual? = item.map {
            switch $0 {
            case .builtIn(let c): return c.visual(override: store.builtInColors[c])
            case .custom(let c): return c.visual
            }
        }
        return HStack(spacing: 14) {
            if let visual { Circle().fill(visual.stripe.0).frame(width: 12, height: 12) }
            else { Circle().strokeBorder(.white.opacity(0.35), lineWidth: 1.2).frame(width: 12, height: 12) }
            Text(title(key)).font(RealFont.inter(15, .medium)).foregroundStyle(item == nil ? .white.opacity(0.6) : .white).lineLimit(1)
            Spacer()
            Button { withAnimation { _ = hidden.insert(key) } } label: {
                Image(systemName: "eye.slash").font(.system(size: 15)).foregroundStyle(Color(argb: 0xFFAAAAB4)).frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(title(key)) gizle")
        }
        .padding(.leading, 16).padding(.vertical, 8).padding(.trailing, 4)
        .frame(minHeight: 56)
        .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.05)))
    }

    private func stepButton(_ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(RealFont.inter(20)).foregroundStyle(enabled ? .white.opacity(0.9) : .white.opacity(0.25))
                .frame(width: 40, height: 40)
        }
        .buttonStyle(.plain).disabled(!enabled)
    }
}

/// ReminderColorPicker.kt "Kart renkleri": Ana renk / Geçiş rengi horizontal rows (✕ = none), the draggable
/// gradient "Geçiş" bar when both are set, otherwise a preview strip, and the dark-text radio.
struct CardColorsPanel: View {
    var title: String? = "Kart renkleri"
    @Binding var start: Int?
    @Binding var end: Int?
    @Binding var blend: Double
    @Binding var darkText: Bool
    var allowNoneStart = true
    var showDarkText = false
    var isStarred = false

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            if let title { Text(title).font(RealFont.inter(13, .semibold)).foregroundStyle(Color(argb: 0xFFF0F0F4)) }
            ColorChoiceRow(label: "Ana renk", selected: $start, allowNone: allowNoneStart)
            ColorChoiceRow(label: "Geçiş rengi", selected: $end, allowNone: true)
            if let s = paletteColor(start), let e = paletteColor(end) {
                HStack(spacing: 0) {
                    Text("Geçiş").font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFFB7B7C0)).frame(width: 96, alignment: .leading)
                    GradientTransitionBar(fraction: $blend, startColor: s, endColor: e)
                }
            } else {
                let fill: AnyShapeStyle = {
                    if let s = paletteColor(start) { return AnyShapeStyle(LinearGradient(colors: [s, s.opacity(0.55)], startPoint: .leading, endPoint: .trailing)) }
                    if let e = paletteColor(end) { return AnyShapeStyle(LinearGradient(colors: [e.opacity(0.55), e], startPoint: .leading, endPoint: .trailing)) }
                    return AnyShapeStyle(LinearGradient(colors: [Color(argb: 0xFF26262E), Color(argb: 0xFF17171B)], startPoint: .leading, endPoint: .trailing))
                }()
                RoundedRectangle(cornerRadius: 9).fill(fill).frame(height: 18)
                    .overlay(RoundedRectangle(cornerRadius: 9).strokeBorder(isStarred ? .white.opacity(0.85) : .white.opacity(0.14), lineWidth: isStarred ? 1.25 : 0.7))
            }
            if showDarkText {
                Button { darkText.toggle() } label: {
                    HStack(spacing: 10) {
                        ZStack {
                            Circle().fill(darkText ? Color(argb: 0xFF2C5696).opacity(0.85) : .white.opacity(0.08))
                            Circle().strokeBorder(darkText ? RealColors.selection : .white.opacity(0.2), lineWidth: 1)
                            if darkText { RealSymbol("checkmark", size: 11.5).foregroundStyle(.white) }
                        }
                        .frame(width: 20, height: 20)
                        Text("Koyu renk metin kullan (Siyah)").font(RealFont.inter(13, .medium))
                            .foregroundStyle(darkText ? Color(argb: 0xFFF5F5F8) : Color(argb: 0xFFB7B7C0))
                    }
                    .padding(.vertical, 4).padding(.horizontal, 2)
                }
            }
        }
        .padding(.horizontal, 15).padding(.vertical, 13)
    }
}

private struct ColorChoiceRow: View {
    let label: String
    @Binding var selected: Int?
    var allowNone = true

    var body: some View {
        HStack(spacing: 0) {
            Text(label).font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFFB7B7C0)).frame(width: 96, alignment: .leading)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if allowNone {
                        let on = selected == nil
                        Button { selected = nil } label: {
                            RealSymbol("xmark", size: 12.6).foregroundStyle(on ? .white : Color(argb: 0xFF878792))
                                .frame(width: 31, height: 31)
                                .background(Circle().fill(on ? Color(argb: 0xFF2D2D36) : Color(argb: 0xFF1B1B1F)))
                                .overlay(Circle().strokeBorder(on ? .white : .white.opacity(0.18), lineWidth: on ? 2.2 : 0.8))
                        }
                        .accessibilityLabel("\(label): Renk yok (Varsayılan)")
                    }
                    ForEach(pickerColorChoices) { choice in
                        let on = selected == choice.index
                        Button { selected = choice.index } label: {
                            Circle().fill(choice.color).frame(width: 31, height: 31)
                                .overlay(Circle().strokeBorder(on ? .white : .white.opacity(0.16), lineWidth: on ? 2.2 : 0.8))
                        }
                        .accessibilityLabel("\(label): \(choice.label)")
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }
}

/// ContinuousGradientTransitionBar: start→end gradient track with a ring indicator you drag.
struct GradientTransitionBar: View {
    @Binding var fraction: Double
    let startColor: Color
    let endColor: Color

    var body: some View {
        GeometryReader { geo in
            let r: CGFloat = 7
            let travel = max(geo.size.width - 2 * r, 1)
            ZStack(alignment: .leading) {
                Capsule().fill(LinearGradient(colors: [startColor, endColor], startPoint: .leading, endPoint: .trailing)).frame(height: 18)
                Circle().fill(startColor.lerp(endColor, fraction)).frame(width: 14, height: 14)
                    .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                    .shadow(color: .black.opacity(0.3), radius: 2)
                    .offset(x: CGFloat(fraction) * travel)
            }
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                fraction = Double(min(max((v.location.x - r) / travel, 0), 1))
            })
        }
        .frame(height: 36)
        .accessibilityLabel("Geçiş çubuğu")
    }
}
