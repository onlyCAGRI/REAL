import SwiftUI
import PhotosUI
import MapKit

/// Card detail (ReminderDetailScreen.kt): every content block of a REAL is edited here.
struct ReminderDetailView: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    let reminderId: Int64

    @State private var title = ""
    @State private var schedule = ScheduleDraft()
    @State private var categoryId: Int64?
    @State private var showDateSheet = false
    @State private var showCategoryPicker = false
    @State private var showColorSheet = false
    @State private var showDeleteDialog = false
    @State private var newChecklistText = ""
    @State private var editingStackCard: CardStackCard?
    @State private var photoSelection: [PhotosPickerItem] = []
    @State private var linkDraft = ""
    @State private var editingLink = false
    @State private var editingLocation = false
    @FocusState private var titleFocused: Bool

    private var reminder: ReminderItem? { store.reminder(reminderId) }

    var body: some View {
        Group {
            if let reminder {
                content(reminder)
            } else {
                Color.black.onAppear { dismiss() }
            }
        }
        .background(RealColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }

    private func content(_ r: ReminderItem) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                topBar(r)
                headerCard(r)
                infoRows(r)
                checklistSection(r)
                journalSection(r)
                cardStackSection(r)
                photosSection(r)
                linkSection(r)
                locationSection(r)
                footer(r)
                Color.clear.frame(height: 40)
            }
            .padding(.horizontal, 16)
        }
        .scrollDismissesKeyboard(.interactively)
        .onAppear {
            title = r.text
            schedule = ScheduleDraft(from: r)
            categoryId = r.customCategoryId
            linkDraft = r.referenceUrl ?? ""
        }
        .onChange(of: schedule) { _, new in store.mutate(reminderId) { new.apply(to: &$0) } }
        .onChange(of: categoryId) { _, new in store.mutate(reminderId) { $0.customCategoryId = new } }
        .onChange(of: titleFocused) { _, focused in if !focused { commitTitle() } }
        .onDisappear(perform: commitTitle)
        .onChange(of: photoSelection) { _, items in importPhotos(items) }
        .sheet(isPresented: $showDateSheet) { ScheduleSheet(draft: $schedule) }
        .sheet(isPresented: $showCategoryPicker) { CategoryPickerSheet(selected: $categoryId).presentationDetents([.medium, .large]) }
        .sheet(isPresented: $showColorSheet) { ReminderColorSheet(reminderId: reminderId).presentationDetents([.medium, .large]) }
        .sheet(item: $editingStackCard) { card in CardStackEditor(reminderId: reminderId, card: card) }
        .sheet(isPresented: $editingLocation) { LocationEditor(reminderId: reminderId).presentationDetents([.medium]) }
        .confirmationDialog("Kart silinsin mi?", isPresented: $showDeleteDialog, titleVisibility: .visible) {
            if r.hasRepeat, let occurrence = Recurrence.nextDeletableOccurrence(r, now: .now) {
                Button("Sadece \(RealDates.dayLabel(occurrence.date))", role: .destructive) {
                    store.deleteOccurrence(reminderId, occurrence: occurrence)
                }
                Button("Tüm seri", role: .destructive) { store.delete([reminderId]) }
            } else {
                Button("Sil", role: .destructive) { store.delete([reminderId]) }
            }
            Button("Vazgeç", role: .cancel) {}
        }
    }

    private func commitTitle() {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed != reminder?.text else { return }
        store.mutate(reminderId) { $0.text = trimmed }
    }

    // MARK: Sections

    private func topBar(_ r: ReminderItem) -> some View {
        HStack(spacing: 10) {
            circleButton("chevron.left", label: "Geri") { dismiss() }
            Spacer()
            circleButton(r.isImportant ? "star.fill" : "star", label: "Önemli") {
                store.mutate(reminderId) { $0.isImportant.toggle() }
            }
            ShareLink(item: r.shareText) {
                Image(systemName: "square.and.arrow.up").frame(width: 44, height: 44).background(.ultraThinMaterial, in: Circle())
            }
            circleButton("trash", label: "Sil") { showDeleteDialog = true }
        }
        .foregroundStyle(.white)
        .padding(.top, 8)
    }

    private func circleButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 16, weight: .semibold))
                .frame(width: 44, height: 44).background(.ultraThinMaterial, in: Circle())
        }
        .accessibilityLabel(label)
    }

    private func headerCard(_ r: ReminderItem) -> some View {
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        let accent = reminderAccentGradient(start: r.accentStart, end: r.accentEnd, completed: false,
                                            blend: r.gradientBlendFraction, solidArgb: r.customSolidColorArgb)
        let ink: Color = r.useDarkText ? Color(argb: 0xFF101010) : .white
        return VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 12) {
                Button { withAnimation { store.toggleCompleted(reminderId) } } label: {
                    ZStack {
                        Circle().strokeBorder(ink.opacity(r.isCompleted ? 0 : 0.6), lineWidth: 1.8)
                        if r.isCompleted {
                            Circle().fill(ink.opacity(0.85))
                            Image(systemName: "checkmark").font(.system(size: 12, weight: .bold))
                                .foregroundStyle(r.useDarkText ? .white : .black)
                        }
                    }
                    .frame(width: 26, height: 26)
                    .padding(.top, 4)
                }
                .accessibilityLabel(r.isCompleted ? "Tamamlamayı geri al" : "Tamamla")
                TextField("Hatırlatıcı metni", text: $title, axis: .vertical)
                    .font(RealFont.inter(26))
                    .kerning(-0.6)
                    .foregroundStyle(ink)
                    .strikethrough(r.isCompleted)
                    .focused($titleFocused)
                    .lineLimit(1...8)
            }
            if let category = store.category(r.customCategoryId) {
                HStack(spacing: 6) {
                    Image(systemName: category.icon.symbol)
                    Text(category.name)
                }
                .font(RealFont.inter(13, .medium))
                .foregroundStyle(ink.opacity(0.8))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Capsule().fill(ink.opacity(0.12)))
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, minHeight: 170, alignment: .topLeading)
        .background {
            if let accent { shape.fill(accent) } else { shape.fill(RealColors.cardSurface) }
        }
        .overlay(shape.strokeBorder(r.isImportant ? Color.white.opacity(0.85) : Color.white.opacity(0.1), lineWidth: r.isImportant ? 1.25 : 0.75))
    }

    private func infoRows(_ r: ReminderItem) -> some View {
        VStack(spacing: 0) {
            DetailRow(icon: "calendar", title: "Tarih", value: schedule.isEmpty ? "Uyarı yok" : schedule.summary) { showDateSheet = true }
            if let pattern = Recurrence.patternLabel(r) {
                Divider().overlay(Color.white.opacity(0.06))
                DetailRow(icon: "repeat", title: "Tekrar", value: pattern) { showDateSheet = true }
            }
            if let minutes = r.durationMinutes, let alert = r.alertAt {
                Divider().overlay(Color.white.opacity(0.06))
                DetailRow(icon: "hourglass", title: "Süre",
                          value: "\(Planner.durationLabel(minutes: Int64(minutes))) · Bitiş: \(RealDates.time((alert + Int64(minutes) * 60_000).date))") { showDateSheet = true }
            }
            Divider().overlay(Color.white.opacity(0.06))
            DetailRow(icon: "square.grid.2x2", title: "Kategori", value: store.category(r.customCategoryId)?.name ?? "Kategori yok") {
                showCategoryPicker = true
            }
            Divider().overlay(Color.white.opacity(0.06))
            DetailRow(icon: "paintpalette", title: "Renk", value: colorLabel(r)) { showColorSheet = true }
            if r.isQuick24HourActive() {
                Divider().overlay(Color.white.opacity(0.06))
                DetailRow(icon: "clock.arrow.circlepath", title: "24 hour", value: "Listeden çıkar") {
                    store.mutate(reminderId) { $0.quick24HourUntilAt = nil }
                }
            }
        }
        .detailSurface()
    }

    private func colorLabel(_ r: ReminderItem) -> String {
        if r.customSolidColorArgb != nil { return "Özel renk" }
        let names = [r.accentStart, r.accentEnd].compactMap { $0 }.compactMap { reminderColorChoices.indices.contains($0) ? reminderColorChoices[$0].label : nil }
        return names.isEmpty ? "Varsayılan" : names.joined(separator: " → ")
    }

    @ViewBuilder
    private func checklistSection(_ r: ReminderItem) -> some View {
        DetailSection(title: "Checklist", trailing: r.isChecklist ? "\(r.checklistItems.filter(\.isCompleted).count)/\(r.checklistItems.count)" : nil) {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(r.checklistItems) { item in
                    HStack(spacing: 12) {
                        Button { store.toggleChecklistItem(reminderId, itemId: item.id) } label: {
                            Image(systemName: item.isCompleted ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 20))
                                .foregroundStyle(item.isCompleted ? RealColors.accentBlue : Color(argb: 0xFF626269))
                        }
                        Text(item.text)
                            .font(RealFont.inter(15))
                            .foregroundStyle(item.isCompleted ? RealColors.textMuted : RealColors.text)
                            .strikethrough(item.isCompleted)
                        Spacer()
                        Button {
                            store.mutate(reminderId) { $0.checklistItems.removeAll { $0.id == item.id } }
                        } label: { Image(systemName: "xmark").font(.system(size: 11)) }
                            .foregroundStyle(RealColors.textMuted)
                    }
                    .padding(.vertical, 6)
                }
                HStack(spacing: 12) {
                    Image(systemName: "plus").foregroundStyle(RealColors.textMuted).frame(width: 20)
                    TextField("Madde ekle", text: $newChecklistText)
                        .font(RealFont.inter(15))
                        .submitLabel(.done)
                        .onSubmit {
                            let t = newChecklistText.trimmingCharacters(in: .whitespaces)
                            guard !t.isEmpty else { return }
                            store.mutate(reminderId) {
                                $0.isChecklist = true
                                $0.checklistItems.append(ChecklistItem(id: .now, text: t))
                            }
                            newChecklistText = ""
                        }
                }
                .padding(.vertical, 6)
            }
        }
    }

    private func journalSection(_ r: ReminderItem) -> some View {
        DetailSection(title: "Günlük", trailing: r.journal?.mood.map { "\($0.emoji) \($0.label)" }) {
            VStack(alignment: .leading, spacing: 12) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(JournalMood.allCases) { mood in
                            let on = r.journal?.mood == mood
                            Button {
                                store.mutate(reminderId) { item in
                                    var j = item.journal ?? ReminderJournal(mood: nil, text: "", createdAt: .now, updatedAt: .now)
                                    j.mood = on ? nil : mood
                                    j.updatedAt = .now
                                    item.journal = j
                                }
                            } label: {
                                Text("\(mood.emoji) \(mood.label)")
                                    .font(RealFont.inter(13))
                                    .padding(.horizontal, 12)
                                    .frame(height: 34)
                                    .background(Capsule().fill(on ? RealColors.accentBlue.opacity(0.9) : Color.white.opacity(0.06)))
                                    .foregroundStyle(on ? .black : .white)
                            }
                        }
                    }
                }
                JournalTextField(reminderId: reminderId, initial: r.journal?.text ?? "")
            }
        }
    }

    private func cardStackSection(_ r: ReminderItem) -> some View {
        DetailSection(title: "Kartlar", trailing: r.cardStack.map { "\($0.cards.count)" }) {
            VStack(alignment: .leading, spacing: 12) {
                if let cards = r.cardStack?.cards, !cards.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(cards) { card in
                                StackCardView(card: card).onTapGesture { editingStackCard = card }
                            }
                        }
                    }
                }
                Button {
                    editingStackCard = CardStackCard()
                } label: {
                    Label("Kart ekle", systemImage: "rectangle.stack.badge.plus")
                        .font(RealFont.inter(14, .medium))
                }
                .foregroundStyle(RealColors.accentBlue)
            }
        }
    }

    private func photosSection(_ r: ReminderItem) -> some View {
        DetailSection(title: "Fotoğraflar", trailing: r.attachments.isEmpty ? nil : "\(r.attachments.count)") {
            VStack(alignment: .leading, spacing: 12) {
                if !r.attachments.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(r.attachments) { attachment in
                                AttachmentThumb(attachment: attachment)
                                    .contextMenu {
                                        Button("Kaldır", systemImage: "trash", role: .destructive) {
                                            AttachmentStorage.remove(attachment)
                                            store.mutate(reminderId) { $0.attachments.removeAll { $0.id == attachment.id } }
                                        }
                                    }
                            }
                        }
                    }
                }
                PhotosPicker(selection: $photoSelection, maxSelectionCount: 10, matching: .images) {
                    Label("Fotoğraf ekle", systemImage: "photo.badge.plus").font(RealFont.inter(14, .medium))
                }
                .foregroundStyle(RealColors.accentBlue)
            }
        }
    }

    private func linkSection(_ r: ReminderItem) -> some View {
        DetailSection(title: "Bağlantı", trailing: nil) {
            HStack {
                TextField("https://", text: $linkDraft)
                    .font(RealFont.inter(15))
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onSubmit {
                        let t = linkDraft.trimmingCharacters(in: .whitespaces)
                        store.mutate(reminderId) { $0.referenceUrl = t.isEmpty ? nil : t }
                    }
                if let url = r.referenceUrl.flatMap(normalizedURL) {
                    Button { openURL(url) } label: { Image(systemName: "arrow.up.right.square") }
                        .foregroundStyle(RealColors.accentBlue)
                        .accessibilityLabel("Bağlantıyı aç")
                }
            }
        }
    }

    private func normalizedURL(_ raw: String) -> URL? {
        let t = raw.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { return nil }
        return URL(string: t.contains("://") ? t : "https://\(t)")
    }

    private func locationSection(_ r: ReminderItem) -> some View {
        DetailSection(title: "Konum", trailing: nil) {
            VStack(alignment: .leading, spacing: 10) {
                if let location = r.location {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(location.placeName).font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                        if !location.address.isEmpty { Text(location.address).font(RealFont.inter(13)).foregroundStyle(RealColors.subtext) }
                    }
                    HStack(spacing: 18) {
                        Button("Haritada aç") { openInMaps(location) }
                        Button("Düzenle") { editingLocation = true }
                        Button("Kaldır", role: .destructive) { store.mutate(reminderId) { $0.location = nil } }
                    }
                    .font(RealFont.inter(14, .medium))
                } else {
                    Button { editingLocation = true } label: {
                        Label("Konum ekle", systemImage: "mappin.and.ellipse").font(RealFont.inter(14, .medium))
                    }
                    .foregroundStyle(RealColors.accentBlue)
                }
            }
        }
    }

    private func openInMaps(_ location: ReminderLocation) {
        if let lat = location.latitude, let lon = location.longitude {
            let item = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon)))
            item.name = location.placeName
            item.openInMaps()
        } else if let url = location.mapsUrl.flatMap(URL.init(string:)) {
            openURL(url)
        } else {
            let q = "\(location.placeName) \(location.address)".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            if let url = URL(string: "http://maps.apple.com/?q=\(q)") { openURL(url) }
        }
    }

    private func footer(_ r: ReminderItem) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Oluşturuldu: \(RealDates.reminder(r.createdAt))")
            if r.updatedAt != r.createdAt { Text("Düzenlendi: \(RealDates.reminder(r.updatedAt))") }
            if let done = r.completedAt { Text("Tamamlandı: \(RealDates.reminder(done))") }
        }
        .font(RealType.cardMeta)
        .foregroundStyle(Color(argb: 0xFF6F6F78))
        .padding(.horizontal, 6)
    }

    private func importPhotos(_ items: [PhotosPickerItem]) {
        guard !items.isEmpty else { return }
        Task {
            var added: [ReminderAttachment] = []
            for item in items {
                if let data = try? await item.loadTransferable(type: Data.self), let attachment = AttachmentStorage.save(data) {
                    added.append(attachment)
                }
            }
            await MainActor.run {
                store.mutate(reminderId) { $0.attachments.append(contentsOf: added) }
                photoSelection = []
            }
        }
    }
}

// MARK: - Building blocks

struct DetailRow: View {
    let icon: String
    let title: String
    let value: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).font(.system(size: 15)).foregroundStyle(RealColors.subtext).frame(width: 22)
                Text(title).font(RealFont.inter(15)).foregroundStyle(RealColors.text)
                Spacer()
                Text(value).font(RealFont.inter(14)).foregroundStyle(RealColors.accentBlue).lineLimit(1)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold)).foregroundStyle(Color(argb: 0xFF55555D))
            }
            .padding(.horizontal, 18)
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct DetailSection<Content: View>: View {
    let title: String
    let trailing: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title.uppercased()).font(RealFont.inter(11, .medium)).kerning(1).foregroundStyle(Color(argb: 0xFF85858E))
                Spacer()
                if let trailing { Text(trailing).font(RealFont.inter(12)).foregroundStyle(RealColors.subtext) }
            }
            content
        }
        .padding(18)
        .detailSurface()
    }
}

extension View {
    func detailSurface() -> some View {
        frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(RealColors.cardSurface))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Color.white.opacity(0.06), lineWidth: 0.75))
    }
}

/// Keeps typing local and saves on pause, so the whole store is not rewritten per keystroke.
struct JournalTextField: View {
    @Environment(ReminderStore.self) private var store
    let reminderId: Int64
    let initial: String
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        TextField("Günlük notu", text: $text, axis: .vertical)
            .font(RealFont.inter(15))
            .lineLimit(2...12)
            .focused($focused)
            .onAppear { text = initial }
            .onChange(of: focused) { _, f in if !f { save() } }
            .onDisappear(perform: save)
    }

    private func save() {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard t != (store.reminder(reminderId)?.journal?.text ?? "") else { return }
        store.mutate(reminderId) { item in
            if t.isEmpty && item.journal?.mood == nil { item.journal = nil; return }
            var j = item.journal ?? ReminderJournal(mood: nil, text: "", createdAt: .now, updatedAt: .now)
            j.text = t
            j.updatedAt = .now
            item.journal = j
        }
    }
}

struct StackCardView: View {
    let card: CardStackCard
    var body: some View {
        let color = card.colorArgb.map { Color(argbInt: $0) } ?? Color(argb: 0xFF22232A)
        VStack(alignment: .leading, spacing: 8) {
            if let path = card.imageUri, let image = AttachmentStorage.image(path) {
                Image(uiImage: image).resizable().scaledToFill().frame(height: 80).clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            if !card.title.isEmpty { Text(card.title).font(RealFont.inter(16, .semibold)).lineLimit(2) }
            if !card.body.isEmpty { Text(card.body).font(RealFont.inter(13)).lineLimit(5).opacity(0.85) }
            Spacer(minLength: 0)
        }
        .foregroundStyle(color.rgba.r * 0.3 + color.rgba.g * 0.59 + color.rgba.b * 0.11 > 0.6 ? Color.black : Color.white)
        .padding(14)
        .frame(width: 170, height: 200, alignment: .topLeading)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(color))
    }
}

struct CardStackEditor: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let reminderId: Int64
    @State var card: CardStackCard

    private var isNew: Bool { store.reminder(reminderId)?.cardStack?.cards.contains { $0.id == card.id } != true }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Başlık", text: $card.title)
                TextField("Metin", text: $card.body, axis: .vertical).lineLimit(3...10)
                Section("Renk") {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 10) {
                        ForEach(reminderColorChoices) { choice in
                            let argb = choice.color.argbInt
                            Circle().fill(choice.color).frame(height: 28)
                                .overlay(Circle().strokeBorder(.white, lineWidth: card.colorArgb == argb ? 2 : 0))
                                .onTapGesture { card.colorArgb = card.colorArgb == argb ? nil : argb }
                        }
                    }
                }
                if !isNew {
                    Button("Kartı sil", role: .destructive) {
                        store.mutate(reminderId) { item in
                            item.cardStack?.cards.removeAll { $0.id == card.id }
                            if item.cardStack?.cards.isEmpty == true { item.cardStack = nil }
                        }
                        dismiss()
                    }
                }
            }
            .navigationTitle(isNew ? "Yeni kart" : "Kart")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        store.mutate(reminderId) { item in
                            var stack = item.cardStack ?? CardStackBlock()
                            if let i = stack.cards.firstIndex(where: { $0.id == card.id }) { stack.cards[i] = card } else { stack.cards.append(card) }
                            item.cardStack = stack
                        }
                        dismiss()
                    }
                    .disabled(card.title.trimmingCharacters(in: .whitespaces).isEmpty && card.body.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

struct LocationEditor: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let reminderId: Int64
    @State private var name = ""
    @State private var address = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Yer adı", text: $name)
                TextField("Adres", text: $address, axis: .vertical)
            }
            .navigationTitle("Konum")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        let n = name.trimmingCharacters(in: .whitespaces)
                        store.mutate(reminderId) { $0.location = n.isEmpty ? nil : ReminderLocation(placeName: n, address: address) }
                        dismiss()
                    }
                }
            }
            .onAppear {
                let l = store.reminder(reminderId)?.location
                name = l?.placeName ?? ""
                address = l?.address ?? ""
            }
        }
    }
}

struct AttachmentThumb: View {
    let attachment: ReminderAttachment
    var body: some View {
        Group {
            if let image = AttachmentStorage.image(attachment.localPath) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "doc").font(.system(size: 24)).foregroundStyle(RealColors.subtext)
            }
        }
        .frame(width: 96, height: 96)
        .background(RealColors.field)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

/// Photos are copied into Application Support/attachments (AttachmentStorage.kt).
enum AttachmentStorage {
    static var directory: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("attachments")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func save(_ data: Data) -> ReminderAttachment? {
        guard let image = UIImage(data: data) else { return nil }
        // Optimise like PhotoOptimizer: long edge 2048, JPEG 0.85.
        let maxEdge: CGFloat = 2048
        let scale = min(1, maxEdge / max(image.size.width, image.size.height))
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let resized = UIGraphicsImageRenderer(size: size).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        guard let jpeg = resized.jpegData(compressionQuality: 0.85) else { return nil }
        let id = UUID().uuidString
        let name = "\(id).jpg"
        do { try jpeg.write(to: directory.appendingPathComponent(name)) } catch { return nil }
        return ReminderAttachment(id: id, localPath: name, type: .image, mimeType: "image/jpeg", displayName: name, sizeBytes: Int64(jpeg.count))
    }

    static func image(_ path: String) -> UIImage? {
        guard !path.isEmpty else { return nil }
        return UIImage(contentsOfFile: directory.appendingPathComponent(path).path)
    }

    static func remove(_ attachment: ReminderAttachment) {
        try? FileManager.default.removeItem(at: directory.appendingPathComponent(attachment.localPath))
    }
}
