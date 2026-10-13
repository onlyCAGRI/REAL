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
            .filter { !($0.notes ?? "").contains("[REAL-CALENDAR:") }
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
    @Environment(\.openURL) private var openURL

    private let muted = Color(argb: 0xFF9A9AA5)
    private var dirty: Bool { draft != connection.options }
    private var datedPlans: [ReminderItem] {
        reminders.reminders.filter { !$0.isCompleted && ($0.alertAt != nil || $0.plannedDateEpochDay != nil) }
    }

    var body: some View {
        SettingsLayout(onBack: onBack, title: "") {
            VStack(alignment: .leading, spacing: 6) {
                Text("Takvim bağlantısı").font(RealFont.inter(28, .semibold)).foregroundStyle(.white)
                Text("Telefondaki Google, iCloud ve diğer takvimleri REAL'de göster; istersen REAL planlarını bir takvime gönder.")
                    .font(RealFont.inter(14)).foregroundStyle(muted).lineSpacing(4)
            }
            .padding(.top, 14).padding(.horizontal, 6)
            section("Nasıl çalışır?") {
                VStack(alignment: .leading, spacing: 10) {
                    Text("REAL'e alınan: Seçtiğin takvimlerin etkinlikleri yalnızca gösterilir. REAL'e kopyalanmaz, onayın olmadan değiştirilmez.")
                    Text("Takvime gönderilen: Yalnız 'Takvime gönder' açıksa, seçtiğin hedef takvime; başlık, gün ve saat. REAL'de sildiğin plan takvimden de silinir.")
                    Text("Ne zaman: Uygulamayı açınca, takvim ekranına girince ve ayar kaydedince. Arka planda sürekli çalışmaz.")
                }
                .font(RealFont.inter(13)).foregroundStyle(muted).lineSpacing(4).padding(16)
            }
            if !connection.status.isEmpty {
                section(connection.busy ? "Eşitleniyor…" : "Durum") {
                    Text(connection.status).font(RealFont.inter(14)).foregroundStyle(.white).padding(16)
                }
            }
            if !connection.readable {
                section("Başlamak için", footer: "REAL, takvim etkinliklerini yalnızca bu izinle okuyabilir.") {
                    SettingsActionRow(icon: .calendar, title: "Takvimleri görmeye izin ver", subtitle: "Google hesabı iPhone'a ekli ve takvimleri açık olmalı.") {
                        Task { _ = await connection.requestRead() }
                    }
                }
            } else {
                section("REAL'de göster", footer: "Seçtiğin takvimlerin etkinlikleri Focus ve takvim ekranlarında görünür.") {
                    if connection.calendars.isEmpty {
                        Text("Takvim bulunamadı. Google hesabını iPhone ayarlarından ekleyip takvimleri aç.").font(RealFont.inter(14)).foregroundStyle(muted).padding(18)
                    }
                    ForEach(Array(connection.calendars.enumerated()), id: \.element.calendarIdentifier) { i, c in
                        if i > 0 { SettingsDivider() }
                        checkRow(c.title, c.source.title, color: Color(cgColor: c.cgColor), checked: draft.readIds.contains(c.calendarIdentifier)) {
                            if draft.readIds.contains(c.calendarIdentifier) { draft.readIds.remove(c.calendarIdentifier) } else { draft.readIds.insert(c.calendarIdentifier) }
                        }
                    }
                }
                section("Takvime gönder", footer: "Kapatmak mevcut takvim kayıtlarını silmez; sonraki değişiklikleri göndermeyi durdurur.") {
                    SettingsToggleRow(icon: .calendar, title: "Takvime gönder", subtitle: "Kapalıyken REAL'den hiçbir şey takvime yazılmaz.", isOn: $draft.exportEnabled)
                }
                if draft.exportEnabled {
                    section("Nereye gönderilsin?", footer: connection.hasLinks ? "Mevcut bağlantılar korunurken hedef takvim değiştirilemez." : nil) {
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
                    section("Hangi planlar?", footer: "Tekrarlı planlarda gelecek 90 gün gönderilir; REAL açıldığında uzatılır. Tarihsiz metin, Pano blokları ve geçici 24 hour kartları gönderilmez.") {
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
            section("İzinler", footer: "Takvimde değiştirilen veya silinen kopyalar korunur, zorla geri yazılmaz.") {
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
            Text("Seçtiğin planların başlığı, günü ve saati takvime yazılır. Tekrarlıların gelecek 90 günü aktarılır. REAL'de sildiğin planların takvimdeki kopyaları da silinir; tamamlananların yalnız gelecek kopyaları kaldırılır. Takvimde sen değiştirdiysen kopyaya dokunulmaz.")
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
                    Text(label).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                    if let supporting, !supporting.isEmpty { Text(supporting).font(RealFont.inter(12)).foregroundStyle(muted).lineLimit(1) }
                }
                Spacer()
                ZStack {
                    if radio { Circle().strokeBorder(checked ? Color(argb: 0xFF91B9FF) : .white.opacity(0.3), lineWidth: 1.5)
                        if checked { Circle().fill(Color(argb: 0xFF91B9FF)).padding(5) } }
                    else { RoundedRectangle(cornerRadius: 6).strokeBorder(checked ? Color(argb: 0xFF91B9FF) : .white.opacity(0.3), lineWidth: 1.5)
                        if checked { ActionIcon(.check, color: Color(argb: 0xFF91B9FF), size: 14) } }
                }
                .frame(width: 22, height: 22)
            }
            .padding(.horizontal, 18).padding(.vertical, 12).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
            Text("Bu etkinlik takvimden gösteriliyor; REAL'e kopyalanmadı.").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF77777F))
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
        }
        .padding(22)
        .presentationDetents([.medium])
        .presentationBackground(Color(argb: 0xFF111113))
    }
}
