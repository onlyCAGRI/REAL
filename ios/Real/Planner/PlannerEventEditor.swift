import SwiftUI

private enum PlannerEditorTool { case people, card, category }

/// PlannerEventEditor.kt: Takvim "+" panel — dimmed calendar behind a glass panel with "Aklında ne var?" + save,
/// Başlangıç / Bitiş tiles (wheel picker), duration chips (30…180 min) and Kişi · Kart · Kategori tools.
struct PlannerEventEditor: View {
    let request: PlannerAddRequest
    let onDismiss: () -> Void

    @Environment(ReminderStore.self) private var store
    @State private var title = ""
    @State private var start = ScheduleDraft()
    @State private var duration: Int?
    @State private var people = ""
    @State private var colorIndex = 0
    @State private var categoryId: Int64?
    @State private var activeTool: PlannerEditorTool?
    @State private var showStart = false
    @State private var showEnd = false
    @State private var endDraft = ScheduleDraft()
    @State private var appeared = false
    @FocusState private var focused: Bool

    private var startDate: Date { start.date ?? Date() }
    private var endDate: Date? { duration.map { startDate.addingTimeInterval(Double($0) * 60) } }
    private var valid: Bool { !title.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(appeared ? 0.34 : 0).ignoresSafeArea()
                .onTapGesture { close() }
            panel
                .padding(.horizontal, 14).padding(.bottom, 12)
                .offset(y: appeared ? 0 : 600)
        }
        .onAppear {
            start = Self.draft(initialStart())
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.34)) { appeared = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { focused = true }
        }
        .realOverlay(isPresented: $showStart) { ScheduleSheet(draft: $start) }
        .realOverlay(isPresented: $showEnd) { ScheduleSheet(draft: $endDraft, initialTimeTab: true) }
        .onChange(of: endDraft) { _, d in
            if let end = d.date { duration = max(1, Int(end.timeIntervalSince(startDate) / 60)) }
        }
    }

    private var panel: some View {
        VStack(alignment: .leading, spacing: 0) {
            Capsule().fill(.white.opacity(0.28)).frame(width: 36, height: 3).frame(maxWidth: .infinity)
            Color.clear.frame(height: 8)
            HStack {
                TextField("", text: $title, prompt: Text("Aklında ne var?").foregroundStyle(Color(argb: 0xFF8E8E96)))
                    .font(RealFont.inter(19)).foregroundStyle(Color(argb: 0xFFF7F7F8)).tint(Color(argb: 0xFF9AB9FF))
                    .focused($focused).submitLabel(.done).onSubmit(save)
                    .padding(.horizontal, 8)
                Button(action: save) {
                    ActionIcon(.check, color: valid ? .white : Color(argb: 0xFF6B6B74), size: 16)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(valid ? Color(argb: 0xFF295799) : Color(argb: 0xFF1E1E24)))
                }
                .buttonStyle(.plain).disabled(!valid)
                .accessibilityLabel("Takvim kartını kaydet")
            }
            .frame(minHeight: 58)
            Color.clear.frame(height: 10)
            HStack(spacing: 8) {
                tile("Başlangıç", "\(Calendar.real.component(.day, from: startDate)) \(Self.month(startDate)) · \(Self.ampm(startDate))") {
                    activeTool = nil; withoutAnimation { showStart = true }
                }
                tile("Bitiş", endDate.map(Self.ampm) ?? "—") {
                    endDraft = Self.draft(endDate ?? startDate.addingTimeInterval(3600))
                    withoutAnimation { showEnd = true }
                }
            }
            Color.clear.frame(height: 8)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach([30, 45, 60, 90, 120, 150, 180], id: \.self) { mins in
                        let sel = duration == mins
                        Button { duration = sel ? nil : mins } label: {
                            Text("\(mins) min").font(RealFont.inter(11, sel ? .semibold : .regular))
                                .foregroundStyle(sel ? Color(argb: 0xFFDCE9FF) : .white.opacity(0.72))
                                .padding(.horizontal, 11).padding(.vertical, 7)
                                .background(RoundedRectangle(cornerRadius: 12).fill(sel ? Color(argb: 0xFF91B9FF).opacity(0.22) : .white.opacity(0.055)))
                                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(sel ? Color(argb: 0xFF91B9FF).opacity(0.55) : .white.opacity(0.09), lineWidth: 0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            Color.clear.frame(height: 10)
            HStack(alignment: .top, spacing: 2) {
                toolButton("Kişi", .person, .people)
                toolButton("Kart", .cards, .card)
                toolButton("Kategori", .category, .category, accent: store.category(categoryId).map { paletteColor($0.accentStart) ?? Color(argb: 0xFF91B9FF) })
            }
            if let tool = activeTool {
                toolBody(tool)
                    .padding(.horizontal, 12).padding(.vertical, 11)
                    .background(RoundedRectangle(cornerRadius: 19).fill(.white.opacity(0.045)))
                    .padding(.top, 8)
                    .transition(.opacity)
            }
            if start.repeatRule != .none {
                Text(start.summary).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF91B9FF)).padding(.leading, 8).padding(.top, 7)
            }
        }
        .padding(.horizontal, 14).padding(.top, 10).padding(.bottom, 12)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous).fill(Color.black.opacity(0.34)))
        .liquidGlass(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .animation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.24), value: activeTool)
    }

    private func tile(_ label: String, _ value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(label).font(RealFont.inter(9)).foregroundStyle(.white.opacity(0.5))
                    Text(value).font(RealFont.inter(15, .medium)).foregroundStyle(.white).lineLimit(1)
                }
                Spacer(minLength: 4)
                ActionIcon(.chevronRight, color: .white.opacity(0.4), size: 13)
            }
            .padding(.horizontal, 12).padding(.vertical, 9)
            .background(RoundedRectangle(cornerRadius: 14).fill(.white.opacity(0.055)))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(.white.opacity(0.10), lineWidth: 0.6))
        }
        .buttonStyle(.plain)
    }

    private func toolButton(_ title: String, _ glyph: ActionGlyph, _ tool: PlannerEditorTool, accent: Color? = nil) -> some View {
        let selected = activeTool == tool
        return Button { activeTool = selected ? nil : tool } label: {
            VStack(spacing: 4) {
                ActionIcon(glyph, color: selected ? Color(argb: 0xFF91B9FF) : (accent ?? Color(argb: 0xFFBABAC3)), size: 20)
                Text(title).font(RealFont.inter(10)).foregroundStyle(selected ? Color(argb: 0xFFDCE9FF) : Color(argb: 0xFF8E8E96)).lineLimit(1)
            }
            .frame(maxWidth: .infinity).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 15).fill(selected ? Color(argb: 0xFF91B9FF).opacity(0.10) : .clear))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func toolBody(_ tool: PlannerEditorTool) -> some View {
        switch tool {
        case .people:
            TextField("", text: $people, prompt: Text("İsimleri virgülle ayır").foregroundStyle(.white.opacity(0.34)))
                .font(RealFont.inter(14)).foregroundStyle(.white).tint(Color(argb: 0xFF9AB9FF))
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 14).fill(.black.opacity(0.18)))
                .onChange(of: people) { _, v in if v.count > 240 { people = String(v.prefix(240)) } }
        case .card:
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 11) {
                    ForEach(realCalendarPastels.indices, id: \.self) { i in
                        Button { colorIndex = i } label: {
                            Circle().fill(realCalendarPastels[i]).frame(width: i == colorIndex ? 34 : 30, height: i == colorIndex ? 34 : 30)
                                .overlay { if i == colorIndex { Circle().fill(Color(argb: 0xFF242322).opacity(0.78)).frame(width: 11, height: 11) } }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Kart rengi \(i + 1)")
                    }
                }
            }
        case .category:
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    categoryChip("Kategori yok", selected: categoryId == nil, accent: Color(argb: 0xFFBABAC3), symbol: nil) { categoryId = nil }
                    ForEach(store.categories) { c in
                        categoryChip(c.name, selected: categoryId == c.id, accent: paletteColor(c.accentStart) ?? Color(argb: 0xFF91B9FF), symbol: c.icon.symbol) {
                            categoryId = c.id
                        }
                    }
                }
            }
        }
    }

    private func categoryChip(_ label: String, selected: Bool, accent: Color, symbol: String?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                if let symbol {
                    RealSymbol(symbol, size: 16).foregroundStyle(selected ? accent : Color(argb: 0xFFB8B8C0))
                } else {
                    Circle().fill(selected ? Color(argb: 0xFF91B9FF) : Color(argb: 0xFF777780)).frame(width: 7, height: 7)
                }
                Text(label).font(RealFont.inter(11)).foregroundStyle(selected ? .white : Color(argb: 0xFFB8B8C0)).lineLimit(1)
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14).fill(selected ? accent.opacity(0.16) : .white.opacity(0.045)))
        }
        .buttonStyle(.plain)
    }

    private func initialStart() -> Date {
        let cal = Calendar.real
        let hour = request.hour ?? cal.component(.hour, from: Date())
        return cal.date(bySettingHour: hour, minute: 0, second: 0, of: request.day) ?? request.day
    }

    private func save() {
        guard valid else { return }
        let list = people.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        var seen = Set<String>()
        let unique = list.filter { seen.insert($0).inserted }.prefix(12)
        let rule = start.repeatRule
        _ = store.addReminder(text: title.trimmingCharacters(in: .whitespaces), alertAt: startDate.epochMillis, repeatRule: rule,
                              categoryId: categoryId) { item in
            item.durationMinutes = duration
            item.people = Array(unique)
            item.plannerColorIndex = colorIndex
            item.repeatWeekdaysMask = start.weekdaysMask
            item.repeatUntilEpochDay = start.untilEpochDay
        }
        NotificationScheduler.requestAuthorization()
        close()
    }

    private func close() {
        focused = false
        withAnimation(.timingCurve(0.4, 0, 1, 1, duration: 0.2)) { appeared = false }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { onDismiss() }
    }

    private static func draft(_ date: Date) -> ScheduleDraft {
        var d = ScheduleDraft()
        d.date = date
        d.hasTime = true
        return d
    }

    private static func month(_ d: Date) -> String {
        ["OCA", "ŞUB", "MAR", "NİS", "MAY", "HAZ", "TEM", "AĞU", "EYL", "EKİ", "KAS", "ARA"][Calendar.real.component(.month, from: d) - 1]
    }

    /// formatTimeAmPm: "12:00 am".
    private static func ampm(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "h:mm a"
        return f.string(from: d).lowercased()
    }
}
