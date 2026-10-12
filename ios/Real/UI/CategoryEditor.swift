import SwiftUI

/// Two-ended palette picker shared by cards and categories (ReminderColorPicker.kt).
struct PaletteGradientPicker: View {
    @Binding var start: Int?
    @Binding var end: Int?
    @Binding var blend: Double
    @Binding var darkText: Bool
    var allowNone = true
    @State private var editingEnd = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            preview
            Picker("", selection: $editingEnd) {
                Text("Başlangıç").tag(false)
                Text("Bitiş").tag(true)
            }
            .pickerStyle(.segmented)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 6), spacing: 12) {
                if allowNone || editingEnd {
                    Button {
                        if editingEnd { end = nil } else { start = nil }
                    } label: {
                        Circle().strokeBorder(Color.white.opacity(0.35), style: StrokeStyle(lineWidth: 1.5, dash: [3]))
                            .overlay(Image(systemName: "slash.circle").foregroundStyle(.white.opacity(0.5)))
                            .frame(height: 38)
                    }
                    .accessibilityLabel("Renk yok")
                }
                ForEach(reminderColorChoices) { choice in
                    let selected = (editingEnd ? end : start) == choice.index
                    Button {
                        if editingEnd { end = choice.index } else { start = choice.index }
                    } label: {
                        Circle().fill(choice.color)
                            .overlay(Circle().strokeBorder(.white, lineWidth: selected ? 2.5 : 0).padding(-4))
                            .frame(height: 38)
                    }
                    .accessibilityLabel(choice.label)
                }
            }
            if start != nil && end != nil {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Geçiş").font(RealFont.inter(12)).foregroundStyle(RealColors.subtext)
                    Slider(value: $blend, in: 0...1)
                }
            }
            Toggle("Koyu yazı", isOn: $darkText)
        }
    }

    private var preview: some View {
        let s = paletteColor(start), e = paletteColor(end)
        let fill: AnyShapeStyle
        switch (s, e) {
        case let (s?, e?): fill = AnyShapeStyle(categoryGradient(start: s, end: e, blend: blend))
        case let (s?, nil): fill = AnyShapeStyle(LinearGradient(colors: [s, s.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing))
        case let (nil, e?): fill = AnyShapeStyle(LinearGradient(colors: [e.opacity(0.55), e], startPoint: .topLeading, endPoint: .bottomTrailing))
        default: fill = AnyShapeStyle(LinearGradient(colors: [Color(argb: 0xFF26262E), Color(argb: 0xFF17171B)], startPoint: .top, endPoint: .bottom))
        }
        return RoundedRectangle(cornerRadius: 20, style: .continuous)
            .fill(fill)
            .frame(height: 64)
            .overlay(Text("Aa").font(RealFont.inter(20, .medium)).foregroundStyle(darkText ? Color(argb: 0xFF101010) : .white))
    }
}

/// Card colour sheet in the detail screen.
struct ReminderColorSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let reminderId: Int64
    @State private var start: Int?
    @State private var end: Int?
    @State private var blend = 0.5
    @State private var dark = false

    var body: some View {
        NavigationStack {
            ScrollView {
                PaletteGradientPicker(start: $start, end: $end, blend: $blend, darkText: $dark).padding(20)
            }
            .navigationTitle("Renk")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        store.mutate(reminderId) {
                            $0.accentStart = start
                            $0.accentEnd = end
                            $0.gradientBlendFraction = (start != nil && end != nil) ? blend : nil
                            $0.customSolidColorArgb = nil
                            $0.useDarkText = dark
                        }
                        dismiss()
                    }
                }
            }
            .onAppear {
                let r = store.reminder(reminderId)
                start = r?.accentStart
                end = r?.accentEnd
                blend = r?.gradientBlendResolved ?? 0.5
                dark = r?.useDarkText ?? false
            }
        }
    }
}

/// Built-in category colour override (long press on a deck tile).
struct BuiltInCategoryColorSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let category: ReminderCategory
    @State private var start: Int?
    @State private var end: Int?
    @State private var blend = 0.5
    @State private var dark = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    CategoryTileView(visual: previewVisual, count: store.items(in: category).count, hero: true)
                    PaletteGradientPicker(start: $start, end: $end, blend: $blend, darkText: $dark)
                    Button("Varsayılana dön") {
                        store.setBuiltInColor(category, nil)
                        dismiss()
                    }
                    .foregroundStyle(RealColors.overdue)
                }
                .padding(20)
            }
            .navigationTitle(category.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        store.setBuiltInColor(category, start == nil && end == nil ? nil
                            : CategoryColorOverride(start: start, end: end, useDarkText: dark, gradientBlendFraction: blend))
                        dismiss()
                    }
                }
            }
            .onAppear {
                let o = store.builtInColors[category]
                start = o?.start
                end = o?.end
                blend = o?.gradientBlendFraction ?? 0.5
                dark = o?.useDarkText ?? category.defaultColors.2
            }
        }
    }

    private var previewVisual: CategoryVisual {
        start == nil && end == nil ? category.visual(override: nil)
            : category.visual(override: CategoryColorOverride(start: start, end: end, useDarkText: dark, gradientBlendFraction: blend))
    }
}

/// Create / edit a custom category (CreateCategoryDialog.kt).
struct CategoryEditorSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let existing: CustomCategory?
    var onCreated: ((CustomCategory) -> Void)?

    @State private var name = ""
    @State private var description = ""
    @State private var icon: CategoryIcon = .work
    @State private var start: Int? = 2
    @State private var end: Int?
    @State private var blend = 0.5
    @State private var dark = false
    @State private var hide = false
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    CategoryTileView(visual: previewVisual, count: 0, description: description, hero: true)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
                Section {
                    TextField("Kategori adı", text: $name)
                    TextField("Açıklama (isteğe bağlı)", text: $description)
                }
                Section("Simge") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 12) {
                        ForEach(CategoryIcon.allCases) { option in
                            Button { icon = option } label: {
                                Image(systemName: option.symbol)
                                    .font(.system(size: 17))
                                    .frame(width: 44, height: 44)
                                    .background(Circle().fill(icon == option ? RealColors.accentBlue : Color.white.opacity(0.06)))
                                    .foregroundStyle(icon == option ? .black : .white)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(option.label)
                        }
                    }
                    .padding(.vertical, 6)
                }
                Section("Renk") {
                    PaletteGradientPicker(start: $start, end: $end, blend: $blend, darkText: $dark, allowNone: false)
                        .padding(.vertical, 6)
                }
                Section {
                    Toggle("Yalnızca kategori içinde göster", isOn: $hide)
                } footer: {
                    Text("Açıkken bu kategorinin kartları ana sayfada ve genel listelerde görünmez.")
                }
                if existing != nil {
                    Section {
                        Button("Kategoriyi sil", role: .destructive) { confirmDelete = true }
                    }
                }
            }
            .navigationTitle(existing == nil ? "Yeni kategori" : "Kategoriyi düzenle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet", action: save)
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .confirmationDialog("Kategori silinsin mi?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Kategoriyi ve kartlarını sil", role: .destructive) { delete(cards: true) }
                Button("Sadece kategoriyi sil, kartlar kalsın", role: .destructive) { delete(cards: false) }
            }
            .onAppear(perform: load)
        }
    }

    private var previewVisual: CategoryVisual {
        CustomCategory(id: 0, name: name.isEmpty ? "Kategori" : name, iconKey: icon.rawValue, accentStart: start ?? 2,
                       accentEnd: end, useDarkText: dark, gradientBlendFraction: blend).visual
    }

    private func load() {
        guard let c = existing else { return }
        name = c.name
        description = c.description
        icon = c.icon
        start = c.accentStart
        end = c.accentEnd
        blend = c.gradientBlendFraction ?? 0.5
        dark = c.useDarkText
        hide = c.hideFromGeneralLists
    }

    private func save() {
        if var c = existing {
            c.name = name.trimmingCharacters(in: .whitespaces)
            c.description = description
            c.iconKey = icon.rawValue
            c.accentStart = start ?? 2
            c.accentEnd = end
            c.gradientBlendFraction = end != nil ? blend : nil
            c.useDarkText = dark
            c.hideFromGeneralLists = hide
            store.updateCategory(c)
        } else {
            let created = store.addCategory(name: name, icon: icon, accentStart: start ?? 2, accentEnd: end, hideFromGeneralLists: hide,
                                            useDarkText: dark, blend: end != nil ? blend : nil, description: description)
            onCreated?(created)
        }
        dismiss()
    }

    private func delete(cards: Bool) {
        guard let c = existing else { return }
        store.deleteCategory(c.id, deleteReminders: cards)
        dismiss()
    }
}

/// Owner name (used by the calendar greeting) and notification permission.
struct SettingsSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var showOrder = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Profil") {
                    TextField("Adın", text: $name)
                }
                Section {
                    Button("Bildirim izni iste") { NotificationScheduler.requestAuthorization() }
                    Button("Kategori sırasını düzenle") { showOrder = true }
                }
                Section {
                    LabeledContent("Kart", value: "\(store.reminders.count)")
                    LabeledContent("Kategori", value: "\(store.categories.count)")
                    LabeledContent("Sürüm", value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-")
                } header: { Text("REAL iOS") }
            }
            .navigationTitle("Hesap")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Bitti") {
                        store.setOwnerName(name)
                        dismiss()
                    }
                }
            }
            .onAppear { name = store.ownerName }
            .sheet(isPresented: $showOrder) { CategoryOrderSheet() }
        }
    }
}

/// Drag to reorder the home deck (CategoryOrderEditorDialog.kt).
struct CategoryOrderSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var items: [OrderedCategory] = []

    var body: some View {
        NavigationStack {
            List {
                ForEach(items) { item in
                    HStack(spacing: 12) {
                        let visual: CategoryVisual = {
                            switch item {
                            case .builtIn(let c): return c.visual(override: store.builtInColors[c])
                            case .custom(let c): return c.visual
                            }
                        }()
                        Image(systemName: visual.symbol).frame(width: 30, height: 30)
                            .background(Circle().fill(visual.stripe.0)).foregroundStyle(visual.foreground)
                        Text(visual.title)
                    }
                }
                .onMove { items.move(fromOffsets: $0, toOffset: $1) }
            }
            .environment(\.editMode, .constant(.active))
            .navigationTitle("Kategori sırası")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        store.setCategoryOrder(items.map(\.orderKey))
                        dismiss()
                    }
                }
            }
            .onAppear { items = store.homeCategoryOrder }
        }
    }
}
