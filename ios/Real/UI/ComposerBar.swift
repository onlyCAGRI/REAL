import SwiftUI
import UIKit

/// Draft schedule chosen in the composer or the detail screen (ReminderDateSheet).
struct ScheduleDraft: Equatable {
    var date: Date? = nil
    var hasTime = true
    var repeatRule: RecurrenceRule = .none
    var weekdaysMask = 0
    var until: Date? = nil
    var durationMinutes: Int? = nil

    var isEmpty: Bool { date == nil }

    var alertAt: EpochMillis? { hasTime ? date?.epochMillis : nil }
    var plannedDay: EpochDay? { hasTime ? nil : date.map { Calendar.real.epochDay(of: $0) } }
    var untilEpochDay: EpochDay? { until.map { Calendar.real.epochDay(of: $0) } }

    var summary: String {
        guard let date else { return "Tarih" }
        var s = hasTime ? RealDates.reminder(date.epochMillis) : RealDates.dayLabel(date)
        if hasTime && repeatRule != .none { s += " • " + repeatRule.displayName }
        return s
    }

    init() {}

    init(from item: ReminderItem) {
        if let alert = item.alertAt {
            date = alert.date
            hasTime = true
        } else if let day = item.plannedDateEpochDay {
            date = Calendar.real.startOfDay(epochDay: day)
            hasTime = false
        }
        repeatRule = item.repeatRule
        weekdaysMask = item.repeatWeekdaysMask
        until = item.repeatUntilEpochDay.map { Calendar.real.startOfDay(epochDay: $0) }
        durationMinutes = item.durationMinutes
    }

    func apply(to item: inout ReminderItem) {
        let oldAlert = item.alertAt
        item.alertAt = alertAt
        item.plannedDateEpochDay = plannedDay
        item.repeatRule = alertAt == nil ? .none : repeatRule
        item.repeatWeekdaysMask = item.repeatRule == .weekly ? weekdaysMask : 0
        item.repeatUntilEpochDay = item.repeatRule == .none ? nil : untilEpochDay
        item.durationMinutes = alertAt == nil ? nil : durationMinutes
        if alertAt != oldAlert { item.anchorAlertAt = alertAt; item.repeatSkippedEpochDays = [] }
        if alertAt != nil || plannedDay != nil { item.quick24HourUntilAt = nil }
    }
}

/// Bottom glass composer (GlassComposer.kt): write a REAL, attach date / category / star / checklist.
struct ComposerBar: View {
    @Environment(ReminderStore.self) private var store
    var defaultCategoryId: Int64?
    var defaultImportant = false
    var defaultPlannedToday = false
    var onOpenPlanner: (() -> Void)?

    @State private var text = ""
    @State private var schedule = ScheduleDraft()
    @State private var categoryId: Int64?
    @State private var important = false
    @State private var checklistMode = false
    @State private var checklistItems: [String] = []
    @State private var checklistDraft = ""
    @State private var showDateSheet = false
    @State private var showCategoryPicker = false
    @FocusState private var focused: Bool
    @FocusState private var checklistFocused: Bool

    private var expanded: Bool { focused || checklistFocused || !text.isEmpty }

    var body: some View {
        VStack(spacing: 10) {
            if expanded {
                if checklistMode { checklistEditor }
                options
            }
            HStack(spacing: 10) {
                if let onOpenPlanner, !expanded {
                    Button(action: onOpenPlanner) {
                        VStack(spacing: 1) {
                            Image(systemName: "calendar").font(.system(size: 19))
                            Text("Takvim").font(RealFont.inter(11, .medium))
                        }
                        .foregroundStyle(.white)
                        .frame(width: 64, height: 56)
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().strokeBorder(.white.opacity(0.12), lineWidth: 0.75))
                    }
                    .accessibilityLabel("Takvim")
                    .transition(.scale.combined(with: .opacity))
                }
                HStack(spacing: 6) {
                    TextField("", text: $text, prompt: Text("Bir REAL yaz…").foregroundStyle(Color(argb: 0xFF8E8E96)), axis: .vertical)
                        .font(RealType.composer)
                        .foregroundStyle(.white)
                        .lineLimit(1...5)
                        .focused($focused)
                        .submitLabel(.done)
                        .onSubmit(submit)
                        .padding(.leading, 18)
                        .padding(.vertical, 16)
                    if expanded {
                        Button(action: submit) {
                            Image(systemName: "arrow.up")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(canSubmit ? .black : Color(argb: 0xFF6A6A72))
                                .frame(width: 40, height: 40)
                                .background(Circle().fill(canSubmit ? Color.white : Color.white.opacity(0.12)))
                        }
                        .disabled(!canSubmit)
                        .padding(.trailing, 8)
                        .accessibilityLabel("Kaydet")
                    }
                }
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(.white.opacity(0.12), lineWidth: 0.75))
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
        .animation(.spring(duration: 0.3), value: expanded)
        .animation(.spring(duration: 0.3), value: checklistMode)
        .onAppear(perform: reset)
        .sheet(isPresented: $showDateSheet) {
            ScheduleSheet(draft: $schedule).presentationDetents([.large])
        }
        .sheet(isPresented: $showCategoryPicker) {
            CategoryPickerSheet(selected: $categoryId).presentationDetents([.medium, .large])
        }
    }

    private var canSubmit: Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !checklistItems.isEmpty
    }

    private var options: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ComposerChip(icon: "calendar", label: schedule.summary, active: !schedule.isEmpty) { showDateSheet = true }
                ComposerChip(icon: store.category(categoryId)?.icon.symbol ?? "square.grid.2x2",
                             label: store.category(categoryId)?.name ?? "Kategori", active: categoryId != nil) { showCategoryPicker = true }
                ComposerChip(icon: important ? "star.fill" : "star", label: "Önemli", active: important) { important.toggle() }
                ComposerChip(icon: "checklist", label: "Checklist", active: checklistMode) {
                    checklistMode.toggle()
                    if checklistMode { checklistFocused = true }
                }
            }
            .padding(.horizontal, 2)
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private var checklistEditor: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(checklistItems.enumerated()), id: \.offset) { index, item in
                HStack {
                    Circle().strokeBorder(Color(argb: 0xFF626269), lineWidth: 1.5).frame(width: 16, height: 16)
                    Text(item).font(RealFont.inter(15)).foregroundStyle(.white)
                    Spacer()
                    Button { checklistItems.remove(at: index) } label: { Image(systemName: "xmark").font(.system(size: 11)) }
                        .foregroundStyle(RealColors.textMuted)
                }
            }
            HStack {
                Image(systemName: "plus").font(.system(size: 13)).foregroundStyle(RealColors.textMuted)
                TextField("Madde ekle", text: $checklistDraft)
                    .font(RealFont.inter(15))
                    .focused($checklistFocused)
                    .submitLabel(.next)
                    .onSubmit {
                        let t = checklistDraft.trimmingCharacters(in: .whitespaces)
                        if !t.isEmpty { checklistItems.append(t) }
                        checklistDraft = ""
                        checklistFocused = true
                    }
            }
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func submit() {
        let pending = checklistDraft.trimmingCharacters(in: .whitespaces)
        if !pending.isEmpty { checklistItems.append(pending) }
        guard canSubmit else { return }
        let title = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let draft = schedule
        store.addReminder(text: title.isEmpty ? "Liste" : title, alertAt: draft.alertAt, plannedDay: draft.plannedDay,
                          repeatRule: draft.repeatRule, categoryId: categoryId, isImportant: important,
                          checklist: checklistMode ? checklistItems : []) { item in
            draft.apply(to: &item)
        }
        if draft.alertAt != nil { NotificationScheduler.requestAuthorization() }
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        reset()
        focused = false
        checklistFocused = false
    }

    private func reset() {
        text = ""
        schedule = ScheduleDraft()
        if defaultPlannedToday {
            schedule.date = Calendar.real.startOfDay(for: Date())
            schedule.hasTime = false
        }
        categoryId = defaultCategoryId
        important = defaultImportant
        checklistMode = false
        checklistItems = []
        checklistDraft = ""
    }
}

struct ComposerChip: View {
    let icon: String
    let label: String
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 13))
                Text(label).font(RealFont.inter(13, .medium)).lineLimit(1)
            }
            .foregroundStyle(active ? Color.black : Color.white)
            .padding(.horizontal, 14)
            .frame(height: 36)
            .background(Capsule().fill(active ? AnyShapeStyle(RealColors.accentBlue) : AnyShapeStyle(.ultraThinMaterial)))
            .overlay(Capsule().strokeBorder(.white.opacity(active ? 0 : 0.12), lineWidth: 0.75))
        }
    }
}

/// Category chooser used by the composer and the detail screen.
struct CategoryPickerSheet: View {
    @Environment(ReminderStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Binding var selected: Int64?
    @State private var showNew = false

    var body: some View {
        NavigationStack {
            List {
                Button {
                    selected = nil
                    dismiss()
                } label: {
                    row(symbol: "circle.dashed", name: "Kategori yok", color: .gray, isSelected: selected == nil)
                }
                ForEach(store.categories) { category in
                    Button {
                        selected = category.id
                        dismiss()
                    } label: {
                        row(symbol: category.icon.symbol, name: category.name, color: category.visual.stripe.0,
                            isSelected: selected == category.id)
                    }
                }
                Button { showNew = true } label: { Label("Yeni kategori", systemImage: "plus") }
            }
            .navigationTitle("Kategori")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Bitti") { dismiss() } } }
            .sheet(isPresented: $showNew) { CategoryEditorSheet(existing: nil) { selected = $0.id } }
        }
    }

    private func row(symbol: String, name: String, color: Color, isSelected: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 14))
                .foregroundStyle(.white)
                .frame(width: 32, height: 32)
                .background(Circle().fill(color))
            Text(name).foregroundStyle(.white)
            Spacer()
            if isSelected { Image(systemName: "checkmark").foregroundStyle(RealColors.accentBlue) }
        }
    }
}
