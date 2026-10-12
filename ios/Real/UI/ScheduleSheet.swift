import SwiftUI

/// Date / time / repeat editor (ReminderDateSheet.kt + DateTimePicker.kt).
struct ScheduleSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var draft: ScheduleDraft
    @State private var working: ScheduleDraft = ScheduleDraft()
    @State private var hasUntil = false

    private let calendar = Calendar.real

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    quickPicks
                }
                Section {
                    DatePicker("Tarih", selection: dateBinding, displayedComponents: .date)
                        .datePickerStyle(.graphical)
                        .environment(\.locale, Locale(identifier: "tr_TR"))
                    Toggle("Saat", isOn: $working.hasTime.animation())
                    if working.hasTime {
                        DatePicker("Saat", selection: dateBinding, displayedComponents: .hourAndMinute)
                            .environment(\.locale, Locale(identifier: "tr_TR"))
                    }
                } footer: {
                    if !working.hasTime {
                        Text("Saatsiz kartlar o günün notu olarak takvimde “All day” bölümünde görünür.")
                    }
                }
                if working.hasTime {
                    Section("Tekrar") {
                        Picker("Tekrar", selection: $working.repeatRule) {
                            ForEach(RecurrenceRule.allCases) { Text($0 == .none ? "Yok" : $0.displayName).tag($0) }
                        }
                        if working.repeatRule == .weekly { weekdayPicker }
                        if working.repeatRule != .none {
                            Toggle("Bitiş tarihi", isOn: $hasUntil.animation())
                            if hasUntil {
                                DatePicker("Son gün", selection: untilBinding, in: (working.date ?? Date())..., displayedComponents: .date)
                                    .environment(\.locale, Locale(identifier: "tr_TR"))
                            }
                        }
                    }
                    Section("Süre") {
                        Picker("Süre", selection: $working.durationMinutes) {
                            Text("Yok").tag(Int?.none)
                            ForEach([15, 30, 45, 60, 90, 120, 180], id: \.self) { m in
                                Text(Planner.durationLabel(minutes: Int64(m))).tag(Int?.some(m))
                            }
                        }
                    }
                }
                if working.date != nil {
                    Section {
                        Button("Tarih ve saati kaldır", role: .destructive) {
                            draft = ScheduleDraft()
                            dismiss()
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(RealColors.sheet)
            .navigationTitle("Tarih")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Vazgeç") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") {
                        var result = working
                        if result.date == nil { result.date = defaultDate() }
                        if !hasUntil { result.until = nil }
                        if result.repeatRule == .weekly && result.weekdaysMask == 0, let d = result.date {
                            result.weekdaysMask = calendar.isoWeekday(of: d).bit
                        }
                        draft = result
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .onAppear {
            working = draft
            hasUntil = draft.until != nil
        }
    }

    private func defaultDate() -> Date {
        // Next full hour, like the Android picker's default.
        let next = calendar.date(byAdding: .hour, value: 1, to: Date())!
        return calendar.date(bySettingHour: calendar.component(.hour, from: next), minute: 0, second: 0, of: next) ?? next
    }

    private var dateBinding: Binding<Date> {
        Binding(get: { working.date ?? defaultDate() }, set: { working.date = $0 })
    }

    private var untilBinding: Binding<Date> {
        Binding(get: { working.until ?? calendar.date(byAdding: .month, value: 1, to: working.date ?? Date())! },
                set: { working.until = $0 })
    }

    private var quickPicks: some View {
        let today = calendar.startOfDay(for: Date())
        let picks: [(String, Date, Bool)] = [
            ("Bugün", today, false),
            ("Bu akşam", calendar.date(bySettingHour: 20, minute: 0, second: 0, of: today)!, true),
            ("Yarın", calendar.date(byAdding: .day, value: 1, to: today)!, false),
            ("Yarın 09:00", calendar.date(bySettingHour: 9, minute: 0, second: 0, of: calendar.date(byAdding: .day, value: 1, to: today)!)!, true),
            ("Haftaya", calendar.date(byAdding: .day, value: 7, to: today)!, false),
        ]
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(picks, id: \.0) { pick in
                    Button(pick.0) {
                        working.date = pick.1
                        working.hasTime = pick.2
                    }
                    .buttonStyle(.bordered)
                    .tint(.white)
                }
            }
        }
        .listRowBackground(Color.clear)
        .listRowInsets(EdgeInsets())
    }

    private var weekdayPicker: some View {
        HStack(spacing: 6) {
            ForEach(Weekday.allCases) { day in
                let on = working.weekdaysMask & day.bit != 0
                Button {
                    working.weekdaysMask ^= day.bit
                } label: {
                    Text(day.short)
                        .font(RealFont.inter(12, .medium))
                        .frame(maxWidth: .infinity, minHeight: 34)
                        .background(Capsule().fill(on ? RealColors.accentBlue : Color.white.opacity(0.08)))
                        .foregroundStyle(on ? .black : .white)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
