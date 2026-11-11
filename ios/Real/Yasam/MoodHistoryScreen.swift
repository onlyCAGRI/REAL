import SwiftUI

/// MoodTrackerHistoryScreen.kt: the same page as Günlük — glow top up to the status bar, a card to add, the month
/// calendar, then entries with a date margin; home moods slide left to delete. A tap opens an entry's details.
struct MoodHistoryScreen: View {
    let onAdd: () -> Void
    var onClose: (() -> Void)? = nil
    @Environment(LifeStore.self) private var life
    @Environment(\.dismiss) private var dismiss
    @State private var swipeOpen: String?
    @State private var open: Set<Int64> = []

    var body: some View {
        let sorted = life.moods.sorted { $0.details.loggedAt > $1.details.loggedAt }
        let tone = YasamPart.mood.glow
        let cal = Calendar.real
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    YasamDiscoveryHero(title: t("Moods"), tone: tone, caption: t("{0} iz", sorted.count), onBack: close)
                    addCard(tone).padding(.horizontal, 16).padding(.top, 14)
                    if sorted.isEmpty {
                        Text(t("Henüz bir mood kaydın yok.")).font(RealFont.inter(14)).foregroundStyle(YasamTone.muted)
                            .padding(.leading, 20).padding(.top, 18)
                    } else {
                        HistoryMonthCard(items: sorted.map { HistoryItem(at: $0.details.loggedAt, color: MoodV2.tint($0.mood), photo: nil, group: $0.mood.rawValue) })
                            .padding(.horizontal, 16).padding(.top, 12)
                    }
                    ForEach(Array(sorted.enumerated()), id: \.element.id) { i, entry in
                        let at = entry.details.loggedAt.date
                        let prev = i > 0 ? sorted[i - 1].details.loggedAt.date : nil
                        if prev == nil || !cal.isDate(prev!, equalTo: at, toGranularity: .month) {
                            YasamCaps(text: Self.monthYear(at)).padding(.leading, 20).padding(.top, 22).padding(.bottom, 8)
                        }
                        let firstOfDay = prev == nil || !cal.isDate(prev!, inSameDayAs: at)
                        SwipeDeleteRow(id: "mood_\(entry.id)", openId: $swipeOpen, radius: 20, surface: Color(argb: 0xFF0D0D0D),
                                       onDelete: { withAnimation(.snappy) { life.removeMood(entry.id) } }) {
                            entryCard(entry, day: firstOfDay ? at : nil)
                        }
                        .padding(.horizontal, 16).padding(.bottom, 8)
                    }
                    Color.clear.frame(height: 34)
                }
            }
            .scrollIndicators(.hidden)
        }
    }

    private func close() { if let onClose { onClose() } else { dismiss() } }

    private static func monthYear(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "LLLL yyyy"
        return f.string(from: d).uppercased(with: I18n.locale)
    }

    private func addCard(_ tone: YasamGlowTone) -> some View {
        Button(action: onAdd) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(t("Şu an nasılsın?")).font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
                    Text(t("Anlık his ya da günün havası.")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
                }
                Spacer()
                Text("+").font(RealFont.inter(22, .bold)).foregroundStyle(.black).frame(width: 40, height: 40).background(Circle().fill(.white))
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.05)))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(tone.light.opacity(0.35), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    /// One mood like a Günlük entry: the day in the margin (only on the day's first mood), then "Anlık his · İyi" and
    /// the time. A tap opens the details (descriptors, factors) downwards.
    private func entryCard(_ entry: MoodTrackerEntry, day: Date?) -> some View {
        let accent = MoodV2.tint(entry.mood)
        let isOpen = open.contains(entry.id)
        let descriptors = entry.details.descriptors.map(MoodV2.descriptorLabel)
        let factors = entry.details.factors.map(Self.factorLabel)
        let hasDetails = !descriptors.isEmpty || !factors.isEmpty
        let weekday: String? = day.map {
            let f = DateFormatter()
            f.locale = I18n.locale
            f.dateFormat = "EEE"
            return f.string(from: $0)
        }
        return HStack(alignment: .top, spacing: 0) {
            VStack(spacing: 0) {
                if let day, let weekday {
                    Text("\(Calendar.real.component(.day, from: day))").font(RealFont.inter(22, .bold)).foregroundStyle(.white)
                    Text(weekday).font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
                }
            }
            .frame(width: 42)
            Rectangle().fill(.white.opacity(0.10)).frame(width: 1, height: 40).padding(.horizontal, 12)
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(entry.details.scope == .moment ? t("Anlık his") : t("Günün havası")).font(RealFont.inter(11, .medium)).foregroundStyle(accent)
                    Spacer()
                    Text(YasamLogText.time(entry.details.loggedAt)).font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
                }
                HStack(spacing: 8) {
                    MoodFace(mood: entry.mood, tint: accent).frame(width: isOpen ? 34 : 24, height: isOpen ? 34 : 24)
                    Text(MoodV2.families[MoodV2.familyIndex(entry.mood)].title).font(RealFont.inter(17, .semibold)).foregroundStyle(.white)
                    if hasDetails { Text(isOpen ? "⌃" : "⌄").font(RealFont.inter(13)).foregroundStyle(YasamTone.muted) }
                }
                .padding(.top, 4)
                if isOpen && !descriptors.isEmpty {
                    Text(descriptors.joined(separator: " · ")).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFC3C3CA)).padding(.top, 8)
                }
                if isOpen && !factors.isEmpty {
                    Text(factors.joined(separator: " · ")).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8F8F98)).padding(.top, 4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .frame(minHeight: 64, alignment: .top)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.05)))
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture {
            guard hasDetails else { return }
            withAnimation(.timingCurve(0.16, 1, 0.3, 1, duration: 0.26)) {
                if isOpen { open.remove(entry.id) } else { open.insert(entry.id) }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(hasDetails ? .isButton : [])
        .accessibilityHint(hasDetails ? (isOpen ? t("Kapat") : t("Ayrıntılar")) : "")
    }

    private static func factorLabel(_ key: String) -> String {
        MoodV2.factorGroups.flatMap(\.1).first { $0.0 == key }.map { t($0.1) } ?? key
    }
}
