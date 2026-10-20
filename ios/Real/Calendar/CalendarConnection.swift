import SwiftUI
import EventKit
import Observation

/// calendar/*: device calendars shown read-only in REAL, and (opt-in) REAL plans written to one target calendar.
/// iOS uses EventKit; Google calendars appear here when the Google account is added to the iPhone.
struct CalendarOptions: Codable, Equatable {
    var readIds: Set<String> = []
    var targetId: String?
    var exportEnabled = false
    var exportAll = false
    var selectedIds: Set<Int64> = []
}

/// CalendarLink: one exported occurrence, with the fingerprint of what REAL last wrote.
struct CalendarLink: Codable, Equatable {
    var key: String
    var eventId: String?
    var fingerprint: String?
    var start: Int64
    var suppressed = false
}

struct CalendarOccurrence: Identifiable, Hashable {
    let id: String
    let calendarId: String
    let title: String
    let start: Date
    let end: Date
    let allDay: Bool
    let calendarName: String
    let color: Color
    let location: String?
    let notes: String?
}

@Observable
final class CalendarConnection {
    static let shared = CalendarConnection()

    private(set) var options: CalendarOptions
    private(set) var status = ""
    private(set) var busy = false
    private(set) var revision = 0
    @ObservationIgnored let store = EKEventStore()
    @ObservationIgnored private let defaults = UserDefaults.standard

    init() {
        options = (try? JSONDecoder().decode(CalendarOptions.self, from: UserDefaults.standard.data(forKey: "calendar_options") ?? Data())) ?? CalendarOptions()
        NotificationCenter.default.addObserver(forName: .EKEventStoreChanged, object: store, queue: .main) { [weak self] _ in self?.revision += 1 }
    }

    var readable: Bool { EKEventStore.authorizationStatus(for: .event) == .fullAccess }
    var writable: Bool {
        let s = EKEventStore.authorizationStatus(for: .event)
        return s == .fullAccess || s == .writeOnly
    }

    func requestRead() async -> Bool {
        let ok = (try? await store.requestFullAccessToEvents()) ?? false
        await MainActor.run { revision += 1 }
        return ok
    }

    var calendars: [EKCalendar] { readable ? store.calendars(for: .event).sorted { $0.title < $1.title } : [] }
    var writableCalendars: [EKCalendar] { calendars.filter { $0.allowsContentModifications } }

    func save(_ next: CalendarOptions) {
        options = next
        if let d = try? JSONEncoder().encode(next) { defaults.set(d, forKey: "calendar_options") }
        revision += 1
    }

    // MARK: Read (RememberExternalCalendarEvents)

    /// Events of the selected calendars in [start, end); REAL's own exported copies are hidden.
    func occurrences(from start: Date, to end: Date) -> [CalendarOccurrence] {
        _ = revision
        guard readable, !options.readIds.isEmpty else { return [] }
        let cals = store.calendars(for: .event).filter { options.readIds.contains($0.calendarIdentifier) }
        guard !cals.isEmpty else { return [] }
        let predicate = store.predicateForEvents(withStart: start, end: end, calendars: cals)
        return store.events(matching: predicate)
            .filter { e in
                let n = e.notes ?? ""
                return !n.hasPrefix("REAL\n[REAL-CALENDAR:") && !n.hasPrefix("REAL planı")
            }
            .map { e in
                CalendarOccurrence(id: (e.eventIdentifier ?? UUID().uuidString) + "@\(Int(e.startDate.timeIntervalSince1970))",
                                   calendarId: e.calendar.calendarIdentifier, title: e.title ?? "Etkinlik",
                                   start: e.startDate, end: e.endDate, allDay: e.isAllDay, calendarName: e.calendar.title,
                                   color: Color(cgColor: e.calendar.cgColor), location: e.location, notes: e.notes)
            }
            .sorted { $0.start < $1.start }
    }

    // MARK: Export (CalendarExporter.reconcile)

    private var links: [String: CalendarLink] {
        get { (try? JSONDecoder().decode([String: CalendarLink].self, from: defaults.data(forKey: "calendar_links") ?? Data())) ?? [:] }
        set { if let d = try? JSONEncoder().encode(newValue) { defaults.set(d, forKey: "calendar_links") } }
    }

    var hasLinks: Bool { !links.isEmpty }

    /// calendarPlans: future occurrences in the next 90 days (date-only → all-day, timed → duration or 60 min).
    func plans(_ reminders: [ReminderItem], now: Date = Date()) -> [(key: String, reminder: ReminderItem, start: Date, end: Date, allDay: Bool)] {
        let cal = Calendar.real
        let startOfToday = cal.startOfDay(for: now)
        let end = cal.date(byAdding: .day, value: 90, to: startOfToday)!
        let chosen = reminders.filter { !$0.isCompleted && (options.exportAll || options.selectedIds.contains($0.id)) }
        return Planner.events(chosen, rangeStart: startOfToday.epochMillis, rangeEnd: end.epochMillis, calendar: cal).compactMap { ev in
            let start = ev.startMillis.date
            if ev.isDateOnly {
                return ("\(ev.reminder.id):\(cal.epochDay(of: start))", ev.reminder, start, cal.date(byAdding: .day, value: 1, to: start)!, true)
            }
            let minutes = min(max(ev.reminder.durationMinutes ?? 60, 1), 1440)
            let stop = start.addingTimeInterval(Double(minutes) * 60)
            guard stop > now else { return nil }
            let key = ev.reminder.repeatRule == .none ? "\(ev.reminder.id):single" : "\(ev.reminder.id):\(cal.epochDay(of: start))"
            return (key, ev.reminder, start, stop, false)
        }
    }

    private func fingerprint(title: String, start: Date, end: Date, allDay: Bool) -> String {
        "\(title)|\(Int(start.timeIntervalSince1970))|\(Int(end.timeIntervalSince1970))|\(allDay)"
    }

    /// Inserts / updates / removes REAL's copies. Copies the user edited in Calendar are left untouched (conflict).
    @discardableResult
    func reconcile(_ reminders: [ReminderItem]) -> String {
        guard options.exportEnabled else { return "" }
        guard writable, let targetId = options.targetId, let target = store.calendar(withIdentifier: targetId) else {
            let msg = "Hedef takvim kullanılamıyor; seçimini kontrol et."
            status = msg
            return msg
        }
        busy = true
        defer { busy = false }
        var current = links
        var inserted = 0, updated = 0, removed = 0, conflicts = 0
        let wanted = plans(reminders)
        let wantedKeys = Set(wanted.map(\.key))
        for p in wanted {
            let fp = fingerprint(title: p.reminder.text, start: p.start, end: p.end, allDay: p.allDay)
            if var link = current[p.key], !link.suppressed {
                if let id = link.eventId, let ev = store.event(withIdentifier: id) {
                    let mine = fingerprint(title: ev.title ?? "", start: ev.startDate, end: ev.endDate, allDay: ev.isAllDay)
                    if mine != link.fingerprint { conflicts += 1; continue }            // edited outside REAL: keep
                    if link.fingerprint == fp { continue }
                    ev.title = p.reminder.text; ev.startDate = p.start; ev.endDate = p.end; ev.isAllDay = p.allDay
                    if (try? store.save(ev, span: .thisEvent, commit: false)) != nil { link.fingerprint = fp; link.start = Int64(p.start.timeIntervalSince1970 * 1000); current[p.key] = link; updated += 1 }
                    continue
                }
                if link.eventId != nil { current[p.key]?.suppressed = true; conflicts += 1; continue } // deleted outside REAL
            }
            let ev = EKEvent(eventStore: store)
            ev.calendar = target
            ev.title = p.reminder.text
            ev.startDate = p.start; ev.endDate = p.end; ev.isAllDay = p.allDay
            ev.notes = "REAL\n[REAL-CALENDAR:\(p.key)]"
            if (try? store.save(ev, span: .thisEvent, commit: false)) != nil {
                current[p.key] = CalendarLink(key: p.key, eventId: ev.eventIdentifier, fingerprint: fp, start: Int64(p.start.timeIntervalSince1970 * 1000))
                inserted += 1
            }
        }
        // Plans that disappeared (deleted, completed, deselected): remove REAL's untouched future copies.
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let aliveIds = Set(reminders.map(\.id))
        for (key, link) in current where !wantedKeys.contains(key) {
            let reminderId = Int64(key.split(separator: ":").first ?? "") ?? -1
            let deleted = !aliveIds.contains(reminderId)
            guard deleted || link.start > nowMs else { continue }
            if let id = link.eventId, let ev = store.event(withIdentifier: id) {
                let mine = fingerprint(title: ev.title ?? "", start: ev.startDate, end: ev.endDate, allDay: ev.isAllDay)
                if mine == link.fingerprint, (try? store.remove(ev, span: .thisEvent, commit: false)) != nil { removed += 1 }
            }
            current[key] = nil
        }
        try? store.commit()
        links = current
        let msg = "Takvim: \(inserted) eklendi, \(updated) güncellendi, \(removed) kaldırıldı." + (conflicts > 0 ? " \(conflicts) dış değişiklik korundu." : "")
        status = msg
        return msg
    }
}

// MARK: - Connection screen (CalendarConnectionScreen.kt)

struct CalendarConnectionScreen: View {
    let onBack: () -> Void
    @Environment(ReminderStore.self) private var reminders
    @State private var connection = CalendarConnection.shared
    @State private var draft = CalendarConnection.shared.options
    @State private var confirm = false
    @State private var error: String?
    @State private var howOpen = false
    @Environment(\.openURL) private var openURL

    private let muted = Color(argb: 0xFF9A9AA5)
    private var dirty: Bool { draft != connection.options }
    private var datedPlans: [ReminderItem] {
        reminders.cards.filter { !$0.isCompleted && ($0.alertAt != nil || $0.plannedDateEpochDay != nil) }
    }

    var body: some View {
        SettingsLayout(onBack: onBack, title: "") {
            VStack(alignment: .leading, spacing: 6) {
                Text("Takvim bağlantısı").font(RealFont.inter(28, .semibold)).foregroundStyle(.white)
                Text("Telefondaki takvimleri REAL'de gör, istersen planlarını takvime gönder.")
                    .font(RealFont.inter(14)).foregroundStyle(muted).lineSpacing(4)
            }
            .padding(.top, 14).padding(.horizontal, 6)
            // The details are one tap away instead of a wall of text.
            SettingsGlassGroup {
                VStack(alignment: .leading, spacing: 0) {
                    Button { withAnimation(.easeOut(duration: 0.2)) { howOpen.toggle() } } label: {
                        HStack {
                            Text("Nasıl çalışır?").font(RealFont.inter(15, .medium)).foregroundStyle(.white)
                            Spacer()
                            RealSymbol("chevron.right", size: 13).foregroundStyle(muted).rotationEffect(.degrees(howOpen ? 90 : 0))
                        }
                        .padding(.horizontal, 18).padding(.vertical, 14).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if howOpen {
                        VStack(alignment: .leading, spacing: 8) {
                            bullet("Takvimden gelenler REAL'de yalnızca gösterilir, değiştirilmez.")
                            bullet("'Takvime gönder' açıksa başlık, gün ve saat seçtiğin takvime yazılır.")
                            bullet("Eşitleme uygulamayı açınca, takvime girince ve ayar kaydedince yapılır; arka planda sürekli çalışmaz.")
                        }
                        .padding(.horizontal, 18).padding(.bottom, 16)
                    }
                }
            }
            .padding(.top, 14)
            if !connection.status.isEmpty {
                SettingsGlassGroup {
                    Text(connection.busy ? "Eşitleniyor…" : connection.status).font(RealFont.inter(14)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 18).padding(.vertical, 14)
                }
                .padding(.top, 12)
            }
            if !connection.readable {
                section("Başlamak için") {
                    SettingsActionRow(icon: .calendar, title: "Takvimleri görmeye izin ver", subtitle: "iPhone'da Google hesabı ve takvimleri açık olmalı.") {
                        Task { _ = await connection.requestRead() }
                    }
                }
            } else {
                section("REAL'de göster") {
                    if connection.calendars.isEmpty {
                        Text("Takvim bulunamadı. iPhone ayarlarından Google hesabını ekleyip takvimleri aç.").font(RealFont.inter(14)).foregroundStyle(muted).padding(18)
                    }
                    ForEach(Array(connection.calendars.enumerated()), id: \.element.calendarIdentifier) { i, c in
                        if i > 0 { SettingsDivider() }
                        checkRow(c.title, c.source.title, color: Color(cgColor: c.cgColor), checked: draft.readIds.contains(c.calendarIdentifier)) {
                            if draft.readIds.contains(c.calendarIdentifier) { draft.readIds.remove(c.calendarIdentifier) } else { draft.readIds.insert(c.calendarIdentifier) }
                        }
                    }
                }
                section("Takvime gönder", footer: draft.exportEnabled ? nil : "Kapatınca takvimdeki mevcut kayıtlar silinmez.") {
                    SettingsToggleRow(icon: .calendar, title: "REAL planlarını takvime gönder", subtitle: "", isOn: $draft.exportEnabled)
                }
                if draft.exportEnabled {
                    section("Hangi takvime?", footer: connection.hasLinks ? "Gönderilmiş planlar varken hedef takvim değiştirilemez." : nil) {
                        if connection.writableCalendars.isEmpty {
                            Text("Yazılabilir takvim bulunamadı.").font(RealFont.inter(14)).foregroundStyle(muted).padding(18)
                        }
                        ForEach(Array(connection.writableCalendars.enumerated()), id: \.element.calendarIdentifier) { i, c in
                            if i > 0 { SettingsDivider() }
                            checkRow(c.title, c.source.title, color: Color(cgColor: c.cgColor), checked: draft.targetId == c.calendarIdentifier, radio: true) {
                                if !connection.hasLinks || connection.options.targetId == nil { draft.targetId = c.calendarIdentifier }
                            }
                        }
                    }
                    section("Hangi planlar?", footer: "Yalnızca tarihi olan kartlar gönderilir. Tekrarlılarda gelecek 90 gün.") {
                        LiquidSegmentedControl(labels: ["Tüm planlı kartlar", "Seçtiklerim"], selected: Binding(get: { draft.exportAll ? 0 : 1 }, set: { draft.exportAll = $0 == 0 }))
                            .frame(height: 40).padding(12)
                        if draft.exportAll {
                            Text("Yeni planlar da otomatik gönderilir.").font(RealFont.inter(13)).foregroundStyle(muted).padding(.horizontal, 18).padding(.bottom, 14)
                        } else if datedPlans.isEmpty {
                            Text("Tarihi veya saati olan plan yok.").font(RealFont.inter(13)).foregroundStyle(muted).padding(.horizontal, 18).padding(.bottom, 14)
                        } else {
                            ForEach(datedPlans) { r in
                                checkRow(r.text, r.alertAt.map { "\(RealDates.dayLabel($0.date)), \(RealDates.time($0.date))" } ?? "Tüm gün", color: nil, checked: draft.selectedIds.contains(r.id)) {
                                    if draft.selectedIds.contains(r.id) { draft.selectedIds.remove(r.id) } else { draft.selectedIds.insert(r.id) }
                                }
                            }
                        }
                    }
                }
            }
            section("İzinler") {
                permissionRow("Takvimi okuma", granted: connection.readable) { Task { _ = await connection.requestRead() } }
                SettingsDivider()
                permissionRow("Takvime yazma", granted: connection.writable) { Task { _ = await connection.requestRead() } }
                SettingsDivider()
                SettingsActionRow(icon: .settings, title: "Uygulama izinlerini aç", subtitle: "") {
                    if let u = URL(string: UIApplication.openSettingsURLString) { openURL(u) }
                }
            }
            if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFBAA7)).padding(.top, 10).padding(.horizontal, 8) }
            Button {
                if draft.exportEnabled && draft.targetId == nil { error = "Göndermek için yazma izni ver ve bir hedef takvim seç."; return }
                if draft.exportEnabled && !connection.options.exportEnabled { confirm = true } else { commit() }
            } label: {
                Text(dirty ? "Değişiklikleri kaydet" : "Kaydedildi").font(RealFont.inter(15, .semibold))
                    .foregroundStyle(dirty ? .black : .white.opacity(0.6))
                    .frame(maxWidth: .infinity).frame(height: 52)
                    .background(Capsule().fill(dirty ? .white : .white.opacity(0.08)))
            }
            .buttonStyle(.plain).disabled(!dirty).padding(.top, 16)
            Color.clear.frame(height: 30)
        }
        .alert("Takvime gönderilsin mi?", isPresented: $confirm) {
            Button("Onayla ve gönder") { commit() }
            Button("Vazgeç", role: .cancel) {}
        } message: {
            Text("• Başlık, gün ve saat takvime yazılır.\n• Tekrarlı planlarda gelecek 90 gün gönderilir.\n• REAL'de sildiğin plan takvimden de silinir. Takvimde değiştirdiğin kopyaya dokunulmaz.")
        }
    }

    private func commit() {
        error = nil
        connection.save(draft)
        connection.reconcile(reminders.reminders)
    }

    private func section<C: View>(_ title: String, footer: String? = nil, @ViewBuilder content: @escaping () -> C) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            SettingsSectionHeader(title: title.uppercased(with: Locale(identifier: "tr_TR")))
            SettingsGlassGroup { content() }
            if let footer { Text(footer).font(RealFont.inter(12)).foregroundStyle(muted).padding(.horizontal, 12).padding(.top, 8) }
        }
    }

    private func checkRow(_ label: String, _ supporting: String?, color: Color?, checked: Bool, radio: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                if let color { Circle().fill(color).frame(width: 10, height: 10) }
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(2)
                    if let supporting, !supporting.isEmpty { Text(supporting).font(RealFont.inter(12)).foregroundStyle(muted).lineLimit(1) }
                }
                Spacer()
                ZStack {
                    if radio {
                        Circle().strokeBorder(checked ? Color(argb: 0xFF91B9FF) : .white.opacity(0.35), lineWidth: 1.6)
                        if checked { Circle().fill(Color(argb: 0xFF91B9FF)).frame(width: 10, height: 10) }
                    } else if checked {
                        Circle().fill(Color(argb: 0xFF91B9FF))
                        ActionIcon(.check, color: .black, size: 13)
                    } else {
                        Circle().strokeBorder(.white.opacity(0.35), lineWidth: 1.6)
                    }
                }
                .frame(width: 24, height: 24)
            }
            .padding(.horizontal, 18).padding(.vertical, 13).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func bullet(_ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Circle().fill(Color(argb: 0xFF91B9FF)).frame(width: 5, height: 5).padding(.top, 7)
            Text(text).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFD6D7DE)).lineSpacing(3)
        }
    }

    private func permissionRow(_ label: String, granted: Bool, action: @escaping () -> Void) -> some View {
        HStack {
            Text(label).font(RealFont.inter(15)).foregroundStyle(.white)
            Spacer()
            Button(action: action) {
                Text(granted ? "Verildi" : "İzin ver").font(RealFont.inter(14, .medium))
                    .foregroundStyle(granted ? muted : Color(argb: 0xFF91B9FF))
            }
            .buttonStyle(.plain).disabled(granted)
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
    }
}

/// ExternalCalendarEventDetail: read-only event sheet with "Takvimde aç".
struct ExternalCalendarEventDetail: View {
    let event: CalendarOccurrence
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(ReminderStore.self) private var store
    @State private var categorizeOpen = false
    @State private var whole = true
    private var links: CalendarCategoryLinks { CalendarCategoryLinks.shared }
    private var linkedCard: Int64? { CalendarCardPublisher.shared.card(for: event, in: store) }

    /// Links a REAL card (in the event's category) to this event and opens it; the event itself is not changed.
    private func addRealContent() {
        let id = linkedCard ?? {
            let made = store.addReminder(text: String(event.title.prefix(2000)), categoryId: links.categoryFor(event))
            CalendarCardPublisher.shared.associate(event, cardId: made.id)
            return made.id
        }()
        dismiss()
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(350))
            NotificationCenter.default.post(name: .realOpenReminder, object: nil, userInfo: ["reminderId": id])
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Circle().fill(event.color).frame(width: 9, height: 9)
                Text(event.calendarName).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF9A9AA5))
            }
            Text(event.title).font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
            Text(event.allDay ? "Tüm gün · " + RealDates.dayLabel(event.start) : "\(RealDates.dayLabel(event.start)), \(RealDates.time(event.start))" + " – " + RealDates.time(event.end))
                .font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.8))
            if let l = event.location, !l.isEmpty { Text(l).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFF9A9AA5)) }
            if let n = event.notes, !n.isEmpty { Text(n).font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.7)).lineLimit(6) }
            Text(linkedCard == nil ? "Bu etkinlik takvimden gösteriliyor; REAL'e kopyalanmadı." : "Bu etkinliğe bağlı bir REAL kartı var.")
                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF77777F))
            HStack(spacing: 10) {
                Button {
                    if let u = URL(string: "calshow:\(event.start.timeIntervalSinceReferenceDate)") { openURL(u) }
                } label: {
                    Text("Takvimde aç").font(RealFont.inter(15, .semibold)).foregroundStyle(.black)
                        .frame(maxWidth: .infinity).frame(height: 48).background(Capsule().fill(.white))
                }
                Button { dismiss() } label: {
                    Text("Kapat").font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                        .frame(maxWidth: .infinity).frame(height: 48).background(Capsule().fill(.white.opacity(0.08)))
                }
            }
            .buttonStyle(.plain).padding(.top, 6)
            Button { addRealContent() } label: {
                Text(linkedCard == nil ? "REAL içeriği ekle" : "REAL kartını aç").font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                    .frame(maxWidth: .infinity).frame(height: 48).background(Capsule().fill(.white.opacity(0.08)))
            }
            .buttonStyle(.plain)
            categorize
        }
        .padding(22)
        .onAppear { whole = links.categoryFor(event) == nil || links.isWholeCalendar(event.calendarId) }
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(argb: 0xFF111113))
    }

    /// Kategorize et: file this event — or everything in its calendar — under a REAL category.
    @ViewBuilder private var categorize: some View {
        let current = store.category(links.categoryFor(event))
        Button { withAnimation(.spring(duration: 0.3)) { categorizeOpen.toggle() } } label: {
            HStack(spacing: 8) {
                if let current { Circle().fill(current.visual.stripe.0).frame(width: 12, height: 12) }
                Text(current?.name ?? "Kategorize et").font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity).frame(height: 48).background(Capsule().fill(.white.opacity(0.08)))
        }
        .buttonStyle(.plain)
        if categorizeOpen {
            VStack(alignment: .leading, spacing: 10) {
                Button { whole.toggle() } label: {
                    HStack(spacing: 10) {
                        ZStack {
                            Circle().strokeBorder(whole ? Color.white : Color(argb: 0xFF777780), lineWidth: 1.2)
                            if whole { Circle().fill(.white); Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(.black) }
                        }
                        .frame(width: 20, height: 20)
                        Text("\(event.calendarName) takvimindeki tüm etkinlikler").font(RealFont.inter(14)).foregroundStyle(.white)
                    }
                }
                .buttonStyle(.plain)
                ForEach(store.categories.filter { !$0.isSharedMirror }) { c in
                    Button { links.link(event, categoryId: c.id, wholeCalendar: whole); categorizeOpen = false } label: {
                        HStack(spacing: 12) {
                            Circle().fill(c.visual.stripe.0).frame(width: 16, height: 16)
                            Text(c.name).font(RealFont.inter(15)).foregroundStyle(.white)
                            Spacer()
                            if c.id == current?.id { Image(systemName: "checkmark").font(.system(size: 13, weight: .semibold)).foregroundStyle(.white) }
                        }
                        .padding(.vertical, 6).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                if !store.categories.contains(where: { $0.name.caseInsensitiveCompare(event.calendarName) == .orderedSame }) {
                    Button {
                        // First palette colour no category uses yet; it can be recoloured later.
                        let used = Set(store.categories.map(\.accentStart))
                        let color = reminderColorDisplayOrder.dropFirst(2).first { !used.contains($0) } ?? 9
                        let made = store.addCategory(name: String(event.calendarName.prefix(60)), icon: .noIcon, accentStart: color, accentEnd: nil)
                        links.link(event, categoryId: made.id, wholeCalendar: whole)
                        categorizeOpen = false
                    } label: {
                        Text("+ Yeni kategori: \(event.calendarName)").font(RealFont.inter(15)).foregroundStyle(.white.opacity(0.85))
                    }
                    .buttonStyle(.plain)
                }
                if current != nil {
                    Button { links.link(event, categoryId: nil, wholeCalendar: false); categorizeOpen = false } label: {
                        Text("Kategoriden çıkar").font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFFFB4AB))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.05)))
            .transition(.opacity.combined(with: .move(edge: .top)))
        }
    }

}
