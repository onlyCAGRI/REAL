import SwiftUI

/// MoodTrackerHistoryScreen.kt: "Yeni bir iz bırak" card on top, then every entry grouped by day.
struct MoodHistoryScreen: View {
    let onAdd: () -> Void
    @Environment(LifeStore.self) private var life
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let sorted = life.moods.sorted { $0.details.loggedAt > $1.details.loggedAt }
        let accent = sorted.first.map { MoodV2.tint($0.mood) } ?? Color(argb: 0xFF9AA8FF)
        let groups = Self.group(sorted)
        ZStack(alignment: .top) {
            LinearGradient(colors: [Color.black.blend(accent, by: 0.2), Color(argb: 0xFF08090C), .black], startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    BackCircle { dismiss() }.accessibilityLabel("Geri")
                    Spacer()
                    Text("Moods").font(RealFont.inter(18, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6))
                    Spacer()
                    Color.clear.frame(width: 40, height: 40)
                }
                .padding(.horizontal, 18).padding(.vertical, 10)
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        addCard(accent)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Geçmiş izlerin").font(RealFont.inter(22, .semibold)).foregroundStyle(Color(argb: 0xFFF3F3F5))
                            Text(sorted.isEmpty ? "Henüz bir mood kaydın yok." : "Moodların günlere göre birlikte gösteriliyor.")
                                .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF85858E))
                        }
                        .padding(.top, 10).padding(.bottom, 2)
                        ForEach(groups, id: \.0) { day, entries in
                            HStack {
                                Text(Self.dayTitle(day)).font(RealFont.inter(17, .semibold)).foregroundStyle(Color(argb: 0xFFF0F0F3))
                                Spacer()
                                Text("\(entries.count) iz").font(RealFont.inter(11)).foregroundStyle(Color(argb: 0xFF777780))
                            }
                            .padding(.top, 12).padding(.bottom, 1)
                            ForEach(entries) { entryCard($0) }
                        }
                    }
                    .padding(.horizontal, 18).padding(.top, 10).padding(.bottom, 34)
                }
                .scrollIndicators(.hidden)
            }
        }
    }

    private static func group(_ entries: [MoodTrackerEntry]) -> [(Date, [MoodTrackerEntry])] {
        let cal = Calendar.real
        var order: [Date] = []
        var map: [Date: [MoodTrackerEntry]] = [:]
        for e in entries {
            let day = cal.startOfDay(for: Date(timeIntervalSince1970: Double(e.details.loggedAt) / 1000))
            if map[day] == nil { order.append(day) }
            map[day, default: []].append(e)
        }
        return order.map { ($0, map[$0]!) }
    }

    private static func dayTitle(_ day: Date) -> String {
        let cal = Calendar.real
        if cal.isDateInToday(day) { return "Bugün" }
        if cal.isDateInYesterday(day) { return "Dün" }
        let f = DateFormatter()
        f.locale = Locale(identifier: "tr_TR")
        f.dateFormat = "d MMMM · EEEE"
        return f.string(from: day)
    }

    private func addCard(_ accent: Color) -> some View {
        Button(action: onAdd) {
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 5) {
                    Text("Yeni bir iz bırak").font(RealFont.inter(24, .medium)).foregroundStyle(.white)
                    Text("Şu an nasıl hissediyorsun ya da bugünün havası nasıldı?").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFC2C2C9))
                        .multilineTextAlignment(.leading).lineSpacing(3)
                }
                Spacer()
                Text("＋").font(.system(size: 28)).foregroundStyle(.white)
            }
            .padding(.horizontal, 22).padding(.vertical, 20)
            .background(LinearGradient(colors: [Color(argb: 0xFF171923).blend(accent, by: 0.3), Color(argb: 0xFF111217).blend(accent, by: 0.13), Color(argb: 0xFF111217)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(accent.opacity(0.38), lineWidth: 0.8))
        }
        .buttonStyle(PressScaleStyle())
    }

    private func entryCard(_ entry: MoodTrackerEntry) -> some View {
        let accent = MoodV2.tint(entry.mood)
        let time = Date(timeIntervalSince1970: Double(entry.details.loggedAt) / 1000).formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
        let descriptors = entry.details.descriptors.prefix(3).map(MoodV2.descriptorLabel)
        let factors = entry.details.factors.prefix(3).map(Self.factorLabel)
        return HStack(spacing: 14) {
            MoodFace(mood: entry.mood, tint: accent).frame(width: 58, height: 58)
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(entry.details.scope == .moment ? "Anlık his" : "Günün havası").font(RealFont.inter(11, .medium)).foregroundStyle(accent.opacity(0.94))
                    Spacer()
                    Text(time).font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF74747D))
                }
                Text(MoodV2.families[MoodV2.familyIndex(entry.mood)].title).font(RealFont.inter(21, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6))
                    .padding(.top, 3)
                if !descriptors.isEmpty {
                    Text(descriptors.joined(separator: " · ")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFC3C3CA)).lineLimit(2).padding(.top, 3)
                }
                if !factors.isEmpty {
                    Text(factors.joined(separator: " · ")).font(RealFont.inter(10)).foregroundStyle(Color(argb: 0xFF7F7F88)).lineLimit(1).padding(.top, 4)
                }
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .background(LinearGradient(colors: [Color(argb: 0xFF17181D).blend(accent, by: 0.18), Color(argb: 0xFF111217)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(.white.opacity(0.13), lineWidth: 0.7))
    }

    private static func factorLabel(_ key: String) -> String {
        MoodV2.factorGroups.flatMap(\.1).first { $0.0 == key }?.1 ?? key
    }
}

struct PressScaleStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.985 : 1).animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
