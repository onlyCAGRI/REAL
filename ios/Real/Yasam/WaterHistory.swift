import SwiftUI

/// One day of water: what was drunk and that day's goal.
struct WaterDay: Codable { let ml: Int; let goal: Int }

/// Water entries themselves are kept 30 days (LifeStore). The Su history wants a year, so every time the screen is
/// shown its days are folded into small per-day totals here (this phone only), kept for 366 days (WaterHistory.kt).
enum WaterArchive {
    private static let key = "yasam_water_days_v1"

    static func record(_ life: LifeStore) -> [EpochDay: WaterDay] {
        let cal = Calendar.real
        var days = (UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode([EpochDay: WaterDay].self, from: $0) }) ?? [:]
        let live = Dictionary(grouping: life.entries.filter { $0.beverage == .water }) { cal.epochDay(ofMillis: $0.createdAt) }
        for (day, list) in live {
            days[day] = WaterDay(ml: list.reduce(0) { $0 + $1.amountMl }, goal: life.dailyGoals[day] ?? life.goalMl)
        }
        let cutoff = cal.epochDay(of: Date()) - 366
        days = days.filter { $0.key >= cutoff }
        UserDefaults.standard.set(try? JSONEncoder().encode(days), forKey: key)
        return days
    }
}

private let water = Color(argb: 0xFF4FB6FF)
private let waterDeep = Color(argb: 0xFF1D5C8C)
private enum WaterRange { case week, month, year }

/// Su > Kayıtlar: Hafta (bars against the goal line), Ay (each day a little glass filled to its share), Yıl (months).
struct WaterHistoryScreen: View {
    let days: [EpochDay: WaterDay]
    let currentGoal: Int
    var onClose: () -> Void
    @State private var range: WaterRange = .week
    @State private var offset = 0

    private let cal = Calendar.real
    private func day(_ d: Date) -> EpochDay { cal.epochDay(of: d) }
    private func ml(_ d: Date) -> Int { days[day(d)]?.ml ?? 0 }
    private func goal(_ d: Date) -> Int { max(days[day(d)]?.goal ?? currentGoal, 1) }

    var body: some View {
        let today = cal.startOfDay(for: Date())
        let (from, to, label) = period(today)
        let counted = stride(from: 0, through: max(cal.dateComponents([.day], from: from, to: min(to, today)).day ?? 0, -1), by: 1)
            .map { cal.date(byAdding: .day, value: $0, to: from)! }
        let logged = counted.filter { ml($0) > 0 }
        let avg = logged.isEmpty ? 0 : logged.reduce(0) { $0 + ml($1) } / logged.count
        let reached = counted.filter { ml($0) >= goal($0) }.count
        let oldest = days.keys.min()
        let canBack = range != .year && (oldest == nil || day(from) > oldest!) && day(from) > day(today) - 365
        ZStack {
            Color.black.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 12) {
                        YasamBackButton(action: onClose)
                        Text(t("Su kaydı")).font(RealFont.inter(24, .bold)).foregroundStyle(.white)
                    }
                    HStack(spacing: 4) {
                        ForEach([(WaterRange.week, t("Hafta")), (.month, t("Ay")), (.year, t("Yıl"))], id: \.1) { r, name in
                            Button { withAnimation(.snappy) { range = r; offset = 0 } } label: {
                                Text(name).font(RealFont.inter(14, .semibold)).foregroundStyle(r == range ? .black : Color(argb: 0xFFCFCFCF))
                                    .frame(maxWidth: .infinity).frame(height: 36)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(r == range ? Color.white : .clear))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(4).background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.07))).padding(.top, 16)
                    HistoryPeriodRow(label: label, canPrev: canBack, canNext: range != .year && offset > 0,
                                     onPrev: { offset += 1 }, onNext: { offset -= 1 })
                        .padding(.top, 10)
                    HStack(spacing: 10) {
                        stat(avg >= 1000 ? "\(yasamOneDecimal(Double(avg) / 1000)) L" : "\(avg) ml", t("günde ortalama"), tint: nil)
                        stat("\(reached)", t("gün hedefe ulaştın"), tint: water)
                    }
                    .padding(.top, 10)
                    Group {
                        switch range {
                        case .week: weekBars(from, today)
                        case .month: monthGlasses(from, to, today)
                        case .year: yearMonths(today)
                        }
                    }
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.05)))
                    .padding(.top, 12)
                    if logged.isEmpty {
                        Text(t("Bu dönemde su kaydı yok.")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted).padding(.top, 14)
                    }
                    Text(t("Geçmiş bu telefonda bir yıl tutulur.")).font(RealFont.inter(11)).foregroundStyle(YasamTone.muted.opacity(0.7)).padding(.top, 14)
                    Color.clear.frame(height: 24)
                }
                .padding(.horizontal, 16).padding(.top, 8)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func period(_ today: Date) -> (Date, Date, String) {
        let short = DateFormatter()
        short.locale = I18n.locale
        short.dateFormat = "d MMM"
        switch range {
        case .week:
            let weekday = (cal.component(.weekday, from: today) + 5) % 7
            let monday = cal.date(byAdding: .day, value: -weekday - 7 * offset, to: today)!
            let sunday = cal.date(byAdding: .day, value: 6, to: monday)!
            return (monday, sunday, "\(short.string(from: monday)) – \(short.string(from: sunday))")
        case .month:
            let first = cal.date(byAdding: .month, value: -offset, to: cal.date(from: cal.dateComponents([.year, .month], from: today))!)!
            return (first, cal.date(byAdding: DateComponents(month: 1, day: -1), to: first)!, YasamLogText.monthTitle(first))
        case .year:
            return (cal.date(byAdding: .day, value: -364, to: today)!, today, t("Son 12 ay"))
        }
    }

    private func stat(_ big: String, _ small: String, tint: Color?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(big).font(RealFont.inter(28, .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
            Text(small).font(RealFont.inter(12)).foregroundStyle(tint != nil ? Color(argb: 0xFFB9E2FF) : YasamTone.muted).lineLimit(2)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(tint?.opacity(0.12) ?? .white.opacity(0.05)))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(tint?.opacity(0.35) ?? .clear, lineWidth: 1))
    }

    /// Seven bars filled up to the day's share; the dashed line is the goal (bars go past it when exceeded).
    private func weekBars(_ monday: Date, _ today: Date) -> some View {
        let week = (0..<7).map { cal.date(byAdding: .day, value: $0, to: monday)! }
        return VStack(spacing: 0) {
            ZStack {
                HStack(alignment: .bottom, spacing: 10) {
                    ForEach(week, id: \.self) { d in
                        let share = min(max(Double(ml(d)) / Double(goal(d)), 0), 1.25) / 1.25
                        GeometryReader { g in
                            ZStack(alignment: .bottom) {
                                RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.white.opacity(0.05))
                                if share > 0 {
                                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(ml(d) >= goal(d) ? water : waterDeep)
                                        .frame(height: g.size.height * share)
                                }
                            }
                        }
                    }
                }
                GeometryReader { g in
                    Path { p in
                        let y = g.size.height * (1 - 1 / 1.25)
                        p.move(to: CGPoint(x: 0, y: y))
                        p.addLine(to: CGPoint(x: g.size.width, y: y))
                    }
                    .stroke(.white.opacity(0.45), style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                }
                .allowsHitTesting(false)
            }
            .frame(height: 180)
            HStack(spacing: 10) {
                ForEach(week, id: \.self) { d in
                    let isToday = cal.isDate(d, inSameDayAs: today)
                    Text(YasamLogText.weekdayShort(d)).font(RealFont.inter(11, isToday ? .bold : .regular))
                        .foregroundStyle(isToday ? .white : YasamTone.muted).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 8)
        }
    }

    /// A month where every day is a small glass filled to its share of that day's goal.
    private func monthGlasses(_ first: Date, _ last: Date, _ today: Date) -> some View {
        let lead = (cal.component(.weekday, from: first) + 5) % 7
        let count = cal.component(.day, from: last)
        let monday = cal.date(byAdding: .day, value: -((cal.component(.weekday, from: today) + 5) % 7), to: today)!
        let shape = UnevenRoundedRectangle(topLeadingRadius: 8, bottomLeadingRadius: 12, bottomTrailingRadius: 12, topTrailingRadius: 8, style: .continuous)
        return VStack(spacing: 6) {
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    Text(YasamLogText.weekdayShort(cal.date(byAdding: .day, value: i, to: monday)!)).font(RealFont.inter(11))
                        .foregroundStyle(YasamTone.muted).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<((lead + count + 6) / 7), id: \.self) { week in
                HStack(spacing: 6) {
                    ForEach(0..<7, id: \.self) { col in
                        let n = week * 7 + col - lead + 1
                        if n >= 1 && n <= count {
                            let d = cal.date(byAdding: .day, value: n - 1, to: first)!
                            let share = min(max(Double(ml(d)) / Double(goal(d)), 0), 1)
                            let future = d > today
                            Color.clear.aspectRatio(0.8, contentMode: .fit)
                                .overlay {
                                    GeometryReader { g in
                                        ZStack(alignment: .bottom) {
                                            shape.fill(future ? .clear : .white.opacity(0.06))
                                            if share > 0 { Rectangle().fill(ml(d) >= goal(d) ? water : waterDeep).frame(height: g.size.height * share) }
                                        }
                                        .clipShape(shape)
                                        .overlay(alignment: .topLeading) {
                                            Text("\(n)").font(RealFont.inter(10)).foregroundStyle(future ? Color(argb: 0xFF555555) : .white.opacity(0.85)).padding(4)
                                        }
                                        .overlay(shape.strokeBorder(cal.isDate(d, inSameDayAs: today) ? .white : .clear, lineWidth: 2))
                                    }
                                }
                                .frame(maxWidth: .infinity)
                        } else {
                            Color.clear.aspectRatio(0.8, contentMode: .fit).frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }

    /// The last 12 months: daily average as a bar and how many days reached the goal.
    private func yearMonths(_ today: Date) -> some View {
        let thisMonth = cal.date(from: cal.dateComponents([.year, .month], from: today))!
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "LLL"
        let rows: [(String, Int, Int)] = (0..<12).map { back in
            let month = cal.date(byAdding: .month, value: -back, to: thisMonth)!
            let n = cal.range(of: .day, in: .month, for: month)!.count
            let list = (0..<n).map { cal.date(byAdding: .day, value: $0, to: month)! }.filter { $0 <= today }
            let logged = list.filter { ml($0) > 0 }
            let avg = logged.isEmpty ? 0 : logged.reduce(0) { $0 + ml($1) } / logged.count
            return (f.string(from: month).capitalized(with: I18n.locale), avg, list.filter { ml($0) >= goal($0) }.count)
        }
        let maxAvg = max(rows.map(\.1).max() ?? 0, 1)
        return VStack(spacing: 8) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 0) {
                    Text(row.0).font(RealFont.inter(13)).foregroundStyle(.white).frame(width: 44, alignment: .leading)
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(.white.opacity(0.06))
                            if row.1 > 0 { Capsule().fill(water).frame(width: g.size.width * CGFloat(row.1) / CGFloat(maxAvg)) }
                        }
                    }
                    .frame(height: 14)
                    Text(row.1 > 0 ? "\(yasamOneDecimal(Double(row.1) / 1000)) L" : "–").font(RealFont.inter(12)).foregroundStyle(.white)
                        .frame(width: 48, alignment: .trailing)
                    Text(row.2 > 0 ? "✓\(row.2)" : "").font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFB9E2FF))
                        .frame(width: 36, alignment: .trailing)
                }
            }
        }
    }
}
