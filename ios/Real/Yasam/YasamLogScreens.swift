import SwiftUI

// Me's log screens: Adım (YasamSteps.kt), Kahve (YasamCoffee.kt), the shared Kayıtlar page (YasamDrinkHistory.kt) and
// the swipe-to-delete row (SwipeDelete.kt).

private let swipeRed = Color(argb: 0xFFE5484D)

// MARK: Swipe delete

/// A log row that slides left: a short swipe stops on a red "Sil" (tap it), a long one (past half the row) deletes.
/// [openId]: only one row stays open at a time.
struct SwipeDeleteRow<Content: View>: View {
    let id: String
    @Binding var openId: String?
    var radius: CGFloat = 18
    var surface: Color = .black
    let onDelete: () -> Void
    @ViewBuilder var content: Content
    @State private var offset: CGFloat = 0
    @State private var width: CGFloat = 1
    private let reveal: CGFloat = 88

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        ZStack(alignment: .trailing) {
            (offset < 0 ? swipeRed : .clear)
                .overlay(alignment: .trailing) {
                    Button { delete() } label: {
                        Text(t("Sil")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white)
                            .frame(width: reveal).frame(maxHeight: .infinity).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            content
                .background(surface, in: shape)
                .offset(x: offset)
                .simultaneousGesture(
                    DragGesture(minimumDistance: 14)
                        .onChanged { g in
                            guard abs(g.translation.width) > abs(g.translation.height) else { return }
                            if openId != id { openId = id }
                            let base: CGFloat = offset <= -reveal + 1 && g.translation.width > 0 ? -reveal : 0
                            offset = min(0, max(-width, base + g.translation.width))
                        }
                        .onEnded { g in
                            let fast = g.predictedEndTranslation.width - g.translation.width < -120
                            if offset < -width * 0.5 || (fast && offset < -reveal * 0.5) { delete() }
                            else if offset < -reveal * 0.5 { settle(-reveal) }
                            else { settle(0); if openId == id { openId = nil } }
                        }
                )
        }
        .clipShape(shape)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = max($0, 1) }
        .onChange(of: openId) { _, now in if now != id && offset != 0 { settle(0) } }
        .accessibilityAction(named: t("Sil")) { onDelete() }
    }

    private func settle(_ to: CGFloat) { withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { offset = to } }

    private func delete() {
        withAnimation(.easeIn(duration: 0.18)) { offset = -width }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            openId = nil
            onDelete()
            offset = 0
        }
    }
}

// MARK: Shared bits

enum YasamLogText {
    static let clock: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()
    static func time(_ at: EpochMillis) -> String { clock.string(from: at.date) }

    /// Two-letter weekday ("Pz", "Mo") for a date.
    static func weekdayShort(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "EEE"
        return String(f.string(from: date).prefix(2))
    }

    static func monthTitle(_ date: Date, withYearIfNot year: Int? = nil) -> String {
        let f = DateFormatter()
        f.locale = I18n.locale
        f.dateFormat = "LLLL"
        let name = f.string(from: date).capitalized(with: I18n.locale)
        let y = Calendar.real.component(.year, from: date)
        return y != (year ?? Calendar.real.component(.year, from: Date())) ? "\(name) \(y)" : name
    }
}

/// Small caps section label ("BUGÜN", "EN ÇOK").
struct YasamCaps: View {
    let text: String
    var color: Color = YasamTone.muted
    var body: some View {
        Text(text).font(RealFont.inter(11, .medium)).tracking(1.4).foregroundStyle(color)
    }
}

// MARK: Steps

private enum StepRange: CaseIterable { case day, week, month }

/// Me > Adım: the bar chart fills the top (today brightest, the dashed line is the goal), a dark sheet below with the
/// big number, distance, calories, floors and G / H / A. Typed by hand until HealthKit fills it.
struct YasamStepsScreen: View {
    var onClose: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var range: StepRange = .day
    @State private var version = 0
    @State private var editing = false
    @State private var grow: CGFloat = 0

    private var bars: [(String, Int)] {
        _ = version
        let cal = Calendar.real
        let today = cal.startOfDay(for: Date())
        func steps(_ d: Date) -> Int { YasamManual.steps(cal.epochDay(of: d)) }
        switch range {
        case .day:
            return (0..<7).reversed().map { back in
                let d = cal.date(byAdding: .day, value: -back, to: today)!
                return (YasamLogText.weekdayShort(d), steps(d))
            }
        case .week:
            let f = DateFormatter()
            f.locale = I18n.locale
            f.dateFormat = "d MMM"
            return (0..<4).reversed().map { w in
                let end = cal.date(byAdding: .day, value: -7 * w, to: today)!
                let label = w == 0 ? t("Bu") : f.string(from: cal.date(byAdding: .day, value: -6, to: end)!)
                return (label, (0..<7).reduce(0) { $0 + steps(cal.date(byAdding: .day, value: -$1, to: end)!) })
            }
        case .month:
            let first = cal.date(from: cal.dateComponents([.year, .month], from: today))!
            let f = DateFormatter()
            f.locale = I18n.locale
            f.dateFormat = "LLL"
            return (0..<6).reversed().map { m in
                let month = cal.date(byAdding: .month, value: -m, to: first)!
                let days = m == 0 ? cal.component(.day, from: today) : cal.range(of: .day, in: .month, for: month)!.count
                return (f.string(from: month), (0..<days).reduce(0) { $0 + steps(cal.date(byAdding: .day, value: $1, to: month)!) })
            }
        }
    }

    var body: some View {
        let bars = self.bars
        let total = bars.last?.1 ?? 0
        let goal: Int? = range == .day ? 10_000 : nil
        let maxV = max(bars.map(\.1).max() ?? 0, goal ?? 0, 1)
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()
            VStack(spacing: 0) {
                chart(bars, goal: goal, maxV: maxV)
                sheet(total)
                    .offset(y: -26)
                Spacer(minLength: 0)
            }
            .ignoresSafeArea(edges: .top)
        }
        .sheet(isPresented: $editing) {
            YasamNumberSheet(title: t("Bugün kaç adım?"), initial: YasamManual.steps(), step: 500, max: 100_000,
                             quick: [("3.000", 3000), ("6.000", 6000), ("10.000", 10000)], format: YasamManual.stepsText) {
                YasamManual.setSteps($0)
                version += 1
            }
        }
        .onAppear { withAnimation(reduceMotion ? nil : .easeOut(duration: 0.6)) { grow = 1 } }
        .onChange(of: range) { _, _ in
            grow = reduceMotion ? 1 : 0
            withAnimation(reduceMotion ? nil : .easeOut(duration: 0.6)) { grow = 1 }
        }
    }

    private func chart(_ bars: [(String, Int)], goal: Int?, maxV: Int) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [Color(argb: 0xFF1A1FBF), Color(argb: 0xFF3B44F0), Color(argb: 0xFF2129C8)], startPoint: .top, endPoint: .bottom)
            RadialGradient(colors: [Color(argb: 0x66BEA0FF), .clear], center: UnitPoint(x: 0.85, y: 0.05), startRadius: 0, endRadius: 320)
            GeometryReader { g in
                let safeTop = g.safeAreaInsets.top
                VStack(spacing: 0) {
                    Canvas { ctx, size in
                        let n = CGFloat(bars.count)
                        let gap: CGFloat = 10
                        let w = (size.width - gap * (n - 1)) / n
                        for (i, bar) in bars.enumerated() {
                            let h = max(size.height * CGFloat(bar.1) / CGFloat(maxV), 6) * grow
                            let x = CGFloat(i) * (w + gap)
                            let rect = CGRect(x: x, y: size.height - h, width: w, height: h)
                            let today = i == bars.count - 1
                            ctx.fill(Path(roundedRect: rect, cornerRadius: min(14, w / 2), style: .continuous),
                                     with: .linearGradient(Gradient(colors: today ? [.white, .white.opacity(0.25)] : [.white.opacity(0.55), .white.opacity(0.08)]),
                                                           startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)))
                        }
                        if let goal {
                            let y = size.height * (1 - CGFloat(goal) / CGFloat(maxV))
                            var line = Path()
                            line.move(to: CGPoint(x: 0, y: y))
                            line.addLine(to: CGPoint(x: size.width, y: y))
                            ctx.stroke(line, with: .color(.white.opacity(0.6)), style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                        }
                    }
                    .padding(.top, safeTop + 64)
                    HStack(spacing: 0) {
                        ForEach(Array(bars.enumerated()), id: \.offset) { _, bar in
                            Text(bar.0).font(RealFont.inter(10)).foregroundStyle(.white.opacity(0.8)).lineLimit(1)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.top, 8).padding(.bottom, 34)
                }
                .padding(.horizontal, 18)
                YasamBackButton(action: onClose).padding(.leading, 14).padding(.top, safeTop + 8)
            }
        }
        .frame(height: 360 + topInset)
    }

    private var topInset: CGFloat {
        (UIApplication.shared.connectedScenes.first as? UIWindowScene)?.windows.first?.safeAreaInsets.top ?? 47
    }

    private func sheet(_ total: Int) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Circle().fill(Color(argb: 0xFF5FD0A0)).frame(width: 8, height: 8)
                Text(t("Elle girildi · saat bağlanınca otomatik")).font(RealFont.inter(11, .semibold)).foregroundStyle(Color(argb: 0xFF9A99A4))
                    .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 0) {
                    ForEach([(StepRange.day, t("G")), (.week, t("H")), (.month, t("A"))], id: \.1) { r, label in
                        Button { range = r } label: {
                            Text(label).font(RealFont.inter(12, .semibold)).foregroundStyle(r == range ? .black : Color(argb: 0xFF9A99A4))
                                .padding(.horizontal, 11).padding(.vertical, 5)
                                .background(RoundedRectangle(cornerRadius: 11).fill(r == range ? Color(argb: 0xFFE9E9EF) : .clear))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(r == range ? .isSelected : [])
                    }
                }
                .padding(3).background(RoundedRectangle(cornerRadius: 14).fill(Color(argb: 0xFF1B1B21)))
            }
            Text(total > 0 ? YasamManual.stepsText(total) : "—").font(.system(size: 64, weight: .thin)).foregroundStyle(.white)
                .contentTransition(.numericText()).padding(.top, 14)
            Text(range == .day ? t("adım bugün") : range == .week ? t("adım bu hafta") : t("adım bu ay"))
                .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFB9B8C2))
            HStack(alignment: .top, spacing: 0) {
                stat(yasamOneDecimal(Double(total) * 0.00075), t("km"), t("Mesafe"))
                stat("\(Int(Double(total) * 0.04))", t("kcal"), t("Kalori"))
                stat("—", nil, t("Kat"))
            }
            .padding(.top, 18)
            Button { editing = true } label: {
                Text(t("Bugünü gir")).font(RealFont.inter(15, .bold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity).frame(height: 50).background(Capsule().fill(.white))
            }
            .buttonStyle(.plain)
            .padding(.top, 18)
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(Color(argb: 0xFF0C0C0F)))
        .overlay(RoundedRectangle(cornerRadius: 30, style: .continuous).strokeBorder(Color(argb: 0xFF1F1F26), lineWidth: 1))
        .padding(.horizontal, 10)
    }

    private func stat(_ value: String, _ unit: String?, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .lastTextBaseline, spacing: 2) {
                Text(value).font(.system(size: 24, weight: .thin)).foregroundStyle(.white)
                if let unit { Text(unit).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFB9B8C2)) }
            }
            Text(label).font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFF8A8994))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: Coffee

private let coffeeAccent = Color(argb: 0xFFC8894B)
private let coffeeSoft = Color(argb: 0xFFE8D3BF)
private let caffeineDayMg = 400

/// Me > Kahve: today's cups and caffeine against 400 mg, "Yine aynısı", the 3×3 kinds and today's list (swipe or
/// long press to delete); Kayıtlar opens the week / month page.
struct YasamCoffeeScreen: View {
    var onClose: () -> Void
    @State private var entries = YasamCoffeeLog.load()
    @State private var armedId: String?
    @State private var swipeOpen: String?
    @State private var history = false

    var body: some View {
        let today = YasamCoffeeLog.today(entries)
        let mg = today.reduce(0) { $0 + $1.coffee.mg }
        ZStack {
            Color.black.ignoresSafeArea().onTapGesture { armedId = nil }
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 12) {
                        YasamBackButton(action: onClose)
                        Text(t("Kahve")).font(RealFont.inter(28, .bold)).foregroundStyle(.white)
                        Spacer()
                        YasamHistoryButton { withAnimation(YasamScreen.openAnim) { history = true } }
                    }
                    todayCard(today, mg: mg).padding(.top, 16)
                    if let last = entries.last { againRow(last).padding(.top, 10) }
                    YasamCaps(text: t("NE İÇİYORSUN?")).padding(.top, 18).padding(.bottom, 8)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                        ForEach(CoffeeKind.allCases) { kind in kindCell(kind) }
                    }
                    if !today.isEmpty {
                        YasamCaps(text: t("BUGÜN")).padding(.top, 20).padding(.bottom, 8)
                        VStack(spacing: 6) {
                            ForEach(today.reversed()) { e in row(e) }
                        }
                    }
                    Color.clear.frame(height: 24)
                }
                .padding(.horizontal, 16).padding(.top, 8)
                .animation(.snappy, value: entries)
            }
            .scrollIndicators(.hidden)
            if history {
                YasamDrinkHistory(
                    title: t("Kahve kaydı"),
                    items: entries.map { HistoryItem(at: $0.at, color: $0.coffee.color, photo: nil, group: $0.coffee.label, amount: $0.coffee.mg) },
                    stats: { period, days in
                        let total = period.reduce(0) { $0 + $1.amount }
                        return [
                            HistoryStat(big: "\(period.count)", small: t("fincan · günde {0}", yasamOneDecimal(Double(period.count) / Double(days)))),
                            HistoryStat(big: total >= 1000 ? "~\(yasamOneDecimal(Double(total) / 1000)) g" : "~\(total) mg",
                                        small: t("kafein · günde ~{0} mg", total / days))
                        ]
                    },
                    onClose: { withAnimation(YasamScreen.closeAnim) { history = false } }
                )
                .transition(YasamScreen.meTransition(false))
                .zIndex(2)
            }
        }
    }

    private func add(_ kind: CoffeeKind) {
        YasamCoffeeLog.add(kind)
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        entries = YasamCoffeeLog.load()
    }

    private func remove(_ id: String) {
        YasamCoffeeLog.remove(id)
        armedId = nil
        entries = YasamCoffeeLog.load()
    }

    private func todayCard(_ today: [CoffeeLogEntry], mg: Int) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            YasamCaps(text: t("BUGÜN"), color: Color(argb: 0xFFD9B48F)).font(RealFont.inter(12))
            HStack(alignment: .lastTextBaseline, spacing: 8) {
                Text("\(today.count)").font(RealFont.inter(56, .bold)).foregroundStyle(.white).contentTransition(.numericText())
                Text(t("fincan")).font(RealFont.inter(17)).foregroundStyle(coffeeSoft)
            }
            HStack {
                Text(t("Kafein ~{0} mg", mg)).foregroundStyle(coffeeSoft)
                Spacer()
                Text(t("günlük {0} mg", caffeineDayMg)).foregroundStyle(Color(argb: 0xFFB89B80))
            }
            .font(RealFont.inter(13))
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.1))
                    Capsule().fill(mg > caffeineDayMg ? swipeRed : coffeeAccent)
                        .frame(width: g.size.width * min(max(CGFloat(mg) / CGFloat(caffeineDayMg), 0), 1))
                }
            }
            .frame(height: 8).padding(.top, 8)
            if let last = today.last {
                let late = Calendar.real.component(.hour, from: last.at.date) >= 17
                Text(late ? t("Son kahve {0} · akşam kafeini uykuyu etkileyebilir", YasamLogText.time(last.at)) : t("Son kahve {0}", YasamLogText.time(last.at)))
                    .font(RealFont.inter(12)).foregroundStyle(Color(argb: 0xFFB89B80)).padding(.top, 12)
            }
        }
        .padding(22)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 28, style: .continuous)
            .fill(RadialGradient(colors: [coffeeAccent.opacity(0.42), Color(argb: 0xFF120C07)], center: .center, startRadius: 0, endRadius: 320)))
        .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(coffeeAccent.opacity(0.35), lineWidth: 1))
    }

    private func againRow(_ last: CoffeeLogEntry) -> some View {
        Button { add(last.coffee) } label: {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 14, style: .continuous).fill(last.coffee.color).frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text(t("Yine aynısı")).font(RealFont.inter(15, .semibold)).foregroundStyle(.white)
                    Text("\(last.coffee.label) · ~\(last.coffee.mg) mg").font(RealFont.inter(12)).foregroundStyle(YasamTone.muted).lineLimit(1)
                }
                Spacer()
                Text("+").font(RealFont.inter(20, .bold)).foregroundStyle(.black).frame(width: 34, height: 34).background(Circle().fill(.white))
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(.white.opacity(0.06)))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(.white.opacity(0.18), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func kindCell(_ kind: CoffeeKind) -> some View {
        Button { add(kind) } label: {
            VStack(alignment: .leading, spacing: 0) {
                Circle().fill(kind.color).frame(width: 14, height: 14)
                Spacer(minLength: 10)
                Text(kind.short).font(RealFont.inter(13, .semibold)).foregroundStyle(.white).lineLimit(1)
                Text("~\(kind.mg) mg").font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
            }
            .padding(.horizontal, 12).padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white.opacity(0.05)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(.white.opacity(0.1), lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityHint(t("Ekle"))
    }

    private func row(_ e: CoffeeLogEntry) -> some View {
        let armed = armedId == e.id
        return SwipeDeleteRow(id: e.id, openId: $swipeOpen, onDelete: { remove(e.id) }) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(e.coffee.color).frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 1) {
                    Text(e.coffee.label).font(RealFont.inter(15, .semibold)).foregroundStyle(.white).lineLimit(1)
                    Text("\(YasamLogText.time(e.at)) · ~\(e.coffee.mg) mg").font(RealFont.inter(12)).foregroundStyle(YasamTone.muted)
                }
                Spacer()
                if armed {
                    Button { remove(e.id) } label: {
                        Text(t("Sil")).font(RealFont.inter(14, .semibold)).foregroundStyle(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8).background(RoundedRectangle(cornerRadius: 14).fill(swipeRed))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(.white.opacity(armed ? 0.1 : 0.05)))
            .contentShape(Rectangle())
            .onTapGesture { armedId = nil }
            .onLongPressGesture { armedId = e.id; UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
        }
    }
}

// MARK: Kayıtlar

/// One drink on the Kayıt page: the coloured block (or the photo) and the name it is grouped by.
struct HistoryItem {
    let at: EpochMillis
    let color: Color
    let photo: URL?
    let group: String
    var amount: Int = 0
}

/// The two boxes at the top of the Kayıt page: big text + small text, the second one may be tinted.
struct HistoryStat {
    let big: String
    let small: String
    var tint: Color? = nil
}

private enum HistoryRange { case week, month }

/// Kayıt: shared by Kahve and Alkol. Hafta = every day a column of blocks in each drink's colour; Ay = a calendar whose
/// days show that day's photo (else the drink's colour). [stats] fills the two boxes for the shown period.
struct YasamDrinkHistory: View {
    let title: String
    let items: [HistoryItem]
    let stats: ([HistoryItem], Int) -> [HistoryStat]
    var onClose: () -> Void
    @State private var range: HistoryRange = .week
    @State private var offset = 0

    var body: some View {
        let cal = Calendar.real
        let today = cal.startOfDay(for: Date())
        let (from, to, label) = period(cal, today)
        let period = items.filter { let d = cal.startOfDay(for: $0.at.date); return d >= from && d <= to }
        let shownDays = max((cal.dateComponents([.day], from: from, to: min(to, today)).day ?? 0) + 1, 1)
        ZStack {
            Color.black.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 12) {
                        YasamBackButton(action: onClose)
                        Text(title).font(RealFont.inter(24, .bold)).foregroundStyle(.white)
                    }
                    HStack(spacing: 4) {
                        ForEach([(HistoryRange.week, t("Hafta")), (.month, t("Ay"))], id: \.1) { r, name in
                            Button { withAnimation(.snappy) { range = r; offset = 0 } } label: {
                                Text(name).font(RealFont.inter(14, .semibold)).foregroundStyle(r == range ? .black : Color(argb: 0xFFCFCFCF))
                                    .frame(maxWidth: .infinity).frame(height: 36)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(r == range ? Color.white : .clear))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(4).background(RoundedRectangle(cornerRadius: 16).fill(.white.opacity(0.07))).padding(.top, 16)
                    HistoryPeriodRow(label: label, canNext: offset > 0, onPrev: { offset += 1 }, onNext: { offset -= 1 }).padding(.top, 10)
                    HStack(spacing: 10) {
                        ForEach(Array(stats(period, shownDays).prefix(2).enumerated()), id: \.offset) { _, s in statBox(s) }
                    }
                    .padding(.top, 10)
                    Group {
                        if range == .week { HistoryWeekBlocks(monday: from, today: today, period: period) }
                        else { HistoryMonthGrid(first: from, last: to, today: today, period: period) }
                    }
                    .padding(14)
                    .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.05)))
                    .padding(.top, 12)
                    if period.isEmpty {
                        Text(t("Bu dönemde kayıt yok.")).font(RealFont.inter(13)).foregroundStyle(YasamTone.muted).padding(.top, 14)
                    } else {
                        YasamCaps(text: t("EN ÇOK")).padding(.top, 18).padding(.bottom, 10)
                        top(period)
                    }
                    Color.clear.frame(height: 24)
                }
                .padding(.horizontal, 16).padding(.top, 8)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func period(_ cal: Calendar, _ today: Date) -> (Date, Date, String) {
        if range == .week {
            let weekday = (cal.component(.weekday, from: today) + 5) % 7 // Monday = 0
            let monday = cal.date(byAdding: .day, value: -weekday - 7 * offset, to: today)!
            let sunday = cal.date(byAdding: .day, value: 6, to: monday)!
            let f = DateFormatter()
            f.locale = I18n.locale
            f.dateFormat = "d MMMM"
            let same = cal.component(.month, from: monday) == cal.component(.month, from: sunday)
            let label = same ? "\(cal.component(.day, from: monday)) – \(f.string(from: sunday))" : "\(f.string(from: monday)) – \(f.string(from: sunday))"
            return (monday, sunday, label)
        }
        let first = cal.date(byAdding: .month, value: -offset, to: cal.date(from: cal.dateComponents([.year, .month], from: today))!)!
        let last = cal.date(byAdding: DateComponents(month: 1, day: -1), to: first)!
        return (first, last, YasamLogText.monthTitle(first))
    }

    private func statBox(_ s: HistoryStat) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(s.big).font(RealFont.inter(28, .bold)).foregroundStyle(.white).lineLimit(1).minimumScaleFactor(0.6)
            Text(s.small).font(RealFont.inter(12)).foregroundStyle(s.tint.map { $0.lerp(.white, 0.55) } ?? YasamTone.muted).lineLimit(2)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(s.tint?.opacity(0.12) ?? .white.opacity(0.05)))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(s.tint?.opacity(0.35) ?? .clear, lineWidth: 1))
    }

    private func top(_ period: [HistoryItem]) -> some View {
        var order: [String] = []
        var counts: [String: (Int, Color)] = [:]
        for i in period {
            if counts[i.group] == nil { order.append(i.group) }
            counts[i.group] = ((counts[i.group]?.0 ?? 0) + 1, i.color)
        }
        let groups = order.map { ($0, counts[$0]!.0, counts[$0]!.1) }.sorted { $0.1 > $1.1 }
        let maxN = groups.first?.1 ?? 1
        return VStack(spacing: 10) {
            ForEach(groups.prefix(6), id: \.0) { g, n, color in
                HStack(spacing: 0) {
                    Circle().fill(color).frame(width: 12, height: 12)
                    Text(g).font(RealFont.inter(14)).foregroundStyle(.white).lineLimit(1).frame(width: 110, alignment: .leading).padding(.leading, 10)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(.white.opacity(0.08))
                            Capsule().fill(color).frame(width: geo.size.width * CGFloat(n) / CGFloat(maxN))
                        }
                    }
                    .frame(height: 8)
                    Text("\(n)").font(RealFont.inter(14, .semibold)).foregroundStyle(.white).frame(width: 30, alignment: .trailing)
                }
            }
        }
    }
}

struct HistoryPeriodRow: View {
    let label: String
    var canPrev = true
    let canNext: Bool
    var onPrev: () -> Void
    var onNext: () -> Void
    var body: some View {
        HStack {
            arrow("‹", t("Önceki"), canPrev, onPrev)
            Text(label).font(RealFont.inter(15, .semibold)).foregroundStyle(.white).frame(maxWidth: .infinity)
            arrow("›", t("Sonraki"), canNext, onNext)
        }
    }
    private func arrow(_ glyph: String, _ name: String, _ enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(glyph).font(.system(size: 22)).foregroundStyle(enabled ? .white : .white.opacity(0.2))
                .frame(width: 44, height: 44).contentShape(Circle())
        }
        .buttonStyle(.plain).disabled(!enabled).accessibilityLabel(name)
    }
}

private let blocksShown = 6

/// Seven columns; each drink a rounded block in its colour, oldest at the bottom. More than six: "+n" on top.
private struct HistoryWeekBlocks: View {
    let monday: Date
    let today: Date
    let period: [HistoryItem]
    var body: some View {
        let cal = Calendar.real
        let days = (0..<7).map { cal.date(byAdding: .day, value: $0, to: monday)! }
        VStack(spacing: 0) {
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(days, id: \.self) { day in
                    let list = period.filter { cal.isDate($0.at.date, inSameDayAs: day) }.sorted { $0.at < $1.at }
                    let shown = list.count > blocksShown ? Array(list.suffix(blocksShown - 1)) : list
                    VStack(spacing: 3) {
                        Spacer(minLength: 0)
                        if list.count > blocksShown {
                            Text("+\(list.count - blocksShown + 1)").font(RealFont.inter(11)).foregroundStyle(YasamTone.muted)
                        }
                        ForEach(Array(shown.reversed().enumerated()), id: \.offset) { _, item in
                            RoundedRectangle(cornerRadius: 7, style: .continuous).fill(item.color).frame(height: 24)
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: CGFloat((24 + 3) * blocksShown + 18))
            Rectangle().fill(.white.opacity(0.12)).frame(height: 1).padding(.top, 6)
            HStack(spacing: 8) {
                ForEach(days, id: \.self) { day in
                    let isToday = cal.isDate(day, inSameDayAs: today)
                    Text(YasamLogText.weekdayShort(day)).font(RealFont.inter(11, isToday ? .bold : .regular))
                        .foregroundStyle(isToday ? .white : YasamTone.muted).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 8)
        }
    }
}

/// A month: each day with drinks shows the last photo of that day, else the last drink's colour; a count when more than one.
private struct HistoryMonthGrid: View {
    let first: Date
    let last: Date
    let today: Date
    let period: [HistoryItem]
    var body: some View {
        let cal = Calendar.real
        let lead = (cal.component(.weekday, from: first) + 5) % 7
        let count = cal.component(.day, from: last)
        let weeks = (lead + count + 6) / 7
        let monday = cal.date(byAdding: .day, value: -((cal.component(.weekday, from: Date()) + 5) % 7), to: today)!
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                ForEach(0..<7, id: \.self) { i in
                    Text(YasamLogText.weekdayShort(cal.date(byAdding: .day, value: i, to: monday)!)).font(RealFont.inter(11))
                        .foregroundStyle(YasamTone.muted).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<weeks, id: \.self) { week in
                HStack(spacing: 6) {
                    ForEach(0..<7, id: \.self) { col in
                        let n = week * 7 + col - lead + 1
                        cell(n, count: count, cal: cal)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func cell(_ n: Int, count: Int, cal: Calendar) -> some View {
        if n >= 1 && n <= count {
            let date = cal.date(byAdding: .day, value: n - 1, to: first)!
            let list = period.filter { cal.isDate($0.at.date, inSameDayAs: date) }.sorted { $0.at < $1.at }
            let future = date > today
            let isToday = cal.isDate(date, inSameDayAs: today)
            let photo = list.last { $0.photo.map { FileManager.default.fileExists(atPath: $0.path) } ?? false }?.photo
            let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
            Color.clear.aspectRatio(1, contentMode: .fit)
                .overlay {
                    ZStack(alignment: .topLeading) {
                        shape.fill(list.last?.color ?? (future ? .clear : .white.opacity(0.06)))
                        if let photo, let image = UIImage(contentsOfFile: photo.path) {
                            Image(uiImage: image).resizable().scaledToFill().clipShape(shape)
                        }
                        Text("\(n)").font(RealFont.inter(10)).padding(4)
                            .foregroundStyle(photo != nil ? .white : !list.isEmpty ? .black.opacity(0.6) : future ? Color(argb: 0xFF555555) : YasamTone.muted)
                    }
                    .clipShape(shape)
                    .overlay(shape.strokeBorder(isToday ? .white : future ? .white.opacity(0.08) : .clear, lineWidth: isToday ? 2 : 1))
                }
                .overlay(alignment: .bottomTrailing) {
                    if list.count > 1 {
                        Text("\(list.count)").font(RealFont.inter(10, .bold)).foregroundStyle(.black)
                            .frame(width: 16, height: 16).background(Circle().fill(.white)).offset(x: 3, y: 3)
                    }
                }
                .frame(maxWidth: .infinity)
        } else {
            Color.clear.aspectRatio(1, contentMode: .fit).frame(maxWidth: .infinity)
        }
    }
}

/// Just the Ay calendar with its ‹ month › row, for screens that keep their own list below (Moods).
struct HistoryMonthCard: View {
    let items: [HistoryItem]
    @State private var offset = 0
    var body: some View {
        let cal = Calendar.real
        let today = cal.startOfDay(for: Date())
        let first = cal.date(byAdding: .month, value: -offset, to: cal.date(from: cal.dateComponents([.year, .month], from: today))!)!
        let last = cal.date(byAdding: DateComponents(month: 1, day: -1), to: first)!
        let period = items.filter { let d = cal.startOfDay(for: $0.at.date); return d >= first && d <= last }
        VStack(spacing: 0) {
            HistoryPeriodRow(label: YasamLogText.monthTitle(first), canNext: offset > 0, onPrev: { offset += 1 }, onNext: { offset -= 1 })
                .padding(.bottom, 8)
            HistoryMonthGrid(first: first, last: last, today: today, period: period)
        }
        .padding(14)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(.white.opacity(0.05)))
    }
}
