import SwiftUI

/// Me > Günlüklerim (YasamJournalList.kt + its hero in HomeWidgetContent.kt): the glow hero reaching under the status
/// bar, a writing card, then entries grouped by month — the date in a diary margin, the entry in a card.
struct YasamJournalScreen: View {
    var onClose: () -> Void
    var onWrite: () -> Void
    var onOpen: (Int64) -> Void
    @Environment(ReminderStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = 30
    @State private var appeared = false

    private var journals: [ReminderItem] {
        store.reminders.filter { $0.journal != nil }.sorted { ($0.journal?.createdAt ?? $0.createdAt) > ($1.journal?.createdAt ?? $1.createdAt) }
    }

    var body: some View {
        let list = journals
        let tone = YasamPart.journal.glow
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    YasamDiscoveryHero(title: t("Günlük"), tone: tone, caption: t("{0} giriş", list.count), onBack: onClose)
                    VStack(alignment: .leading, spacing: 0) {
                        writeCard(tone).stagger(0, appeared, reduceMotion)
                        if list.isEmpty {
                            Text(t("Henüz günlük yok. İlk satırı yazınca burada tarihleriyle birikir."))
                                .font(RealFont.inter(14)).foregroundStyle(YasamTone.muted).lineSpacing(4)
                                .padding(.top, 18).padding(.horizontal, 4)
                                .stagger(1, appeared, reduceMotion)
                        } else {
                            let visible = Array(list.prefix(shown))
                            ForEach(Array(visible.enumerated()), id: \.element.id) { index, r in
                                let at = (r.journal?.createdAt ?? r.createdAt).date
                                let prev = index > 0 ? (visible[index - 1].journal?.createdAt ?? visible[index - 1].createdAt).date : nil
                                VStack(alignment: .leading, spacing: 0) {
                                    if prev == nil || !Calendar.real.isDate(prev!, equalTo: at, toGranularity: .month) {
                                        YasamCaps(text: Self.monthYear(at)).padding(.top, 22).padding(.bottom, 8).padding(.leading, 4)
                                    }
                                    entry(r, at: at)
                                }
                                .stagger(index + 1, appeared, reduceMotion)
                            }
                            if list.count > shown {
                                Button { shown += 30 } label: {
                                    Text(t("DAHA FAZLA GÖSTER  ›")).font(RealFont.inter(12, .semibold)).tracking(1.2).foregroundStyle(YasamTone.muted)
                                        .padding(.vertical, 14)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Color.clear.frame(height: 60)
                    }
                    .padding(.horizontal, 16).padding(.top, 14)
                }
            }
            .scrollIndicators(.hidden)
        }
        .onAppear { appeared = true }
    }

    private static func monthYear(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "LLLL yyyy"
        return f.string(from: d).uppercased(with: I18n.locale)
    }

    private func writeCard(_ tone: YasamGlowTone) -> some View {
        Button(action: onWrite) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(t("Bugün ne oldu?")).font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
                    Text(t("Bir cümle bile yeter.")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
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

    private func entry(_ r: ReminderItem, at: Date) -> some View {
        let text = (r.journal?.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let mood = r.journal?.mood
        let weekday: String = {
            let f = DateFormatter()
            f.locale = I18n.locale
            f.dateFormat = "EEE"
            return f.string(from: at)
        }()
        return Button { onOpen(r.id) } label: {
            HStack(alignment: .top, spacing: 0) {
                VStack(spacing: 0) {
                    Text("\(Calendar.real.component(.day, from: at))").font(RealFont.inter(22, .bold)).foregroundStyle(.white)
                    Text(weekday).font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
                }
                .frame(width: 42)
                Rectangle().fill(.white.opacity(0.10)).frame(width: 1, height: 40).padding(.horizontal, 12)
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 0) {
                        Text(YasamLogText.time((r.journal?.createdAt ?? r.createdAt))).font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(YasamTone.muted)
                        if let mood {
                            Circle().fill(YasamTone.mood(mood)).frame(width: 7, height: 7).padding(.leading, 8)
                            Text(" \(mood.label)").font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
                        }
                    }
                    Text(text.isEmpty ? t("Boş sayfa") : text).font(RealFont.inter(15)).lineSpacing(3)
                        .foregroundStyle(.white.opacity(text.isEmpty ? 0.5 : 0.9)).lineLimit(3).multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(minHeight: 64)
            .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.05)))
            .contentShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .padding(.bottom, 8)
        .accessibilityElement(children: .combine)
        .accessibilityHint(t("Günlüğü aç"))
    }
}

private extension View {
    /// Rows slide in 55 ms apart (capped at the 12th), like the Android list.
    func stagger(_ i: Int, _ on: Bool, _ reduce: Bool) -> some View {
        self.opacity(on ? 1 : 0).offset(y: on || reduce ? 0 : 14)
            .animation(reduce ? nil : .timingCurve(0.16, 1, 0.3, 1, duration: 0.46).delay(0.3 + Double(min(i, 12)) * 0.055), value: on)
    }
}
