import SwiftUI
import UIKit

/// List card (ReminderCard.kt): completion circle, title with animated strike-through,
/// checklist/journal/date meta, category stripes and the white ring for starred cards.
struct ReminderCardView: View {
    let reminder: ReminderItem
    var category: CustomCategory?
    var showCategoryStripes = true
    /// Günlüklerim list: no completion circle, mood line instead of dates.
    var journalListMode = false
    var isSelectionMode = false
    var isSelected = false
    var onToggle: () -> Void = {}
    var onOpen: () -> Void = {}
    var onLongPress: () -> Void = {}
    /// Swipe right: into 24 hour (or complete / undo, see [swipeRightLabel]).
    var onSwipeTo24Hour: (() -> Void)?
    /// Swipe left: delete (the caller offers "Geri al").
    var onSwipeDelete: (() -> Void)?
    var swipeRightLabel = "Move 24 hour"
    /// True when the card stays in the list after the right swipe, so it slides back into place.
    var swipeRightKeepsCard = true
    /// Sade kartlar (Ayarlar, deneysel): no completion circle; the text moves left.
    var plainStyle = false
    /// Home search results: the card's category name in a small pill (like the category descriptions).
    var categoryLabel = false

    @State private var dragX: CGFloat = 0
    /// Which way this drag may go: a card is swiped from its edge (Sil from the right side, complete / 24 hour from the
    /// left side); a drag starting in the middle does not move it, so a scroll or a tap cannot throw a card away.
    @State private var mayLeft = false
    @State private var mayRight = false
    @State private var cardWidth: CGFloat = 1
    @State private var recurringAck = false

    private var useDark: Bool { reminder.autoDarkText }
    private var accent: LinearGradient? {
        reminderAccentGradient(start: reminder.accentStart, end: reminder.accentEnd, completed: reminder.isCompleted,
                               blend: reminder.gradientBlendFraction, solidArgb: reminder.customSolidColorArgb)
    }
    private var textColor: Color {
        if useDark { return reminder.isCompleted ? Color(argb: 0xFF55555A) : Color(argb: 0xFF101010) }
        return reminder.isCompleted && !journalListMode ? RealColors.textMuted : RealColors.text
    }
    private var subtext: Color { useDark ? Color(argb: 0xFF282832) : RealColors.subtext }
    private var strike: Bool { !journalListMode && reminder.isCompleted && reminder.journal == nil }
    private var stripes: (Color, Color)? {
        guard showCategoryStripes, reminder.customCategoryId != nil, let category else { return nil }
        return category.visual.stripe
    }
    // Sade kartlar: a completed card swipes right to undo ("Geri al").
    private var swipeRightOn: Bool { onSwipeTo24Hour != nil && (!reminder.isCompleted || plainStyle) }
    private var plain: Bool { plainStyle && !isSelectionMode }
    /// Ortak kategori: a card someone else added carries just their name.
    private var author: String? {
        guard let name = reminder.sharedAuthorName?.trimmingCharacters(in: .whitespaces), !name.isEmpty,
              let uid = reminder.sharedAuthorUid, uid != RealAuth.shared.account?.uid else { return nil }
        return name
    }
    private var swipeLeftOn: Bool { onSwipeDelete != nil }
    private var swipeEnabled: Bool { (swipeRightOn || swipeLeftOn) && !isSelectionMode && !journalListMode }

    var body: some View {
        ZStack {
            if swipeEnabled && dragX != 0 { CardSwipeGlow(x: dragX) }
            if swipeEnabled && swipeRightOn {
                let p = min(max(dragX / 82, 0), 1)
                Text(swipeRightLabel)
                    .font(RealFont.inter(12, .semibold))
                    .foregroundStyle(RealColors.selection)
                    .scaleEffect(0.85 + 0.15 * p, anchor: .leading)
                    .padding(.leading, 18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .opacity(Double(p))
            }
            if swipeEnabled && swipeLeftOn {
                let p = min(max(-dragX / 82, 0), 1)
                HStack(spacing: 6) {
                    Image(systemName: "trash").font(.system(size: 13, weight: .semibold))
                    Text("Sil").font(RealFont.inter(12, .semibold))
                }
                .foregroundStyle(Color(argb: 0xFFFF8A80))
                .scaleEffect(0.85 + 0.15 * p, anchor: .trailing)
                .padding(.trailing, 18)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .opacity(Double(p))
            }
            swipeable
        }
    }

    /// iOS 18+: a UIKit pan that starts only for a sideways move, so the list's vertical scroll never waits on the
    /// card (the SwiftUI DragGesture on every card made scrolling catch). iOS 17 keeps the SwiftUI gesture.
    @ViewBuilder private var swipeable: some View {
        if #available(iOS 18.0, *) {
            card.offset(x: dragX)
                .onGeometryChange(for: CGFloat.self, of: { $0.size.width }) { cardWidth = max(1, $0) }
                .gesture(HorizontalSwipeGesture(enabled: swipeEnabled,
                                                onBegan: { swipeBegan(at: $0) },
                                                onChanged: { swipeChanged($0) },
                                                onEnded: { swipeEnded($0, predicted: $1) }))
        } else {
            card.offset(x: dragX)
                .onGeometryChange(for: CGFloat.self, of: { $0.size.width }) { cardWidth = max(1, $0) }
                .simultaneousGesture(swipeGesture, including: swipeEnabled ? .all : .subviews)
        }
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return HStack(alignment: .top, spacing: 2) {
            if (journalListMode || plain) && !isSelectionMode {
                Color.clear.frame(width: 14, height: 1)
            } else if !isSelectionMode {
                completionCircle
            } else {
                Color.clear.frame(width: 12, height: 48)
            }
            VStack(alignment: .leading, spacing: 0) {
                Text(reminder.text.isEmpty ? "Boş not" : reminder.text)
                    .font(RealType.cardTitle)
                    .lineSpacing(4)
                    .foregroundStyle(textColor)
                    .strikethrough(strike, color: textColor)
                    .lineLimit(3)
                    .animation(.easeOut(duration: 0.18), value: strike)
                meta
            }
            .padding(.top, 11)
            .padding(.bottom, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            if isSelectionMode { selectionDot.padding(.top, 11).padding(.leading, 10) }
        }
        .padding(.leading, 4)
        .padding(.trailing, stripes != nil ? 32 : 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .bottomTrailing) {
            if let author {
                Text(author).font(RealFont.inter(11)).foregroundStyle(textColor.opacity(0.55)).lineLimit(1)
                    .frame(maxWidth: 140, alignment: .trailing)
                    .padding(.trailing, stripes != nil ? 32 : 16).padding(.bottom, 8)
            }
        }
        .background {
            ZStack(alignment: .trailing) {
                if let accent { shape.fill(accent) } else { shape.fill(RealColors.cardSurface) }
                if isSelected { shape.fill(RealColors.accentBlue.opacity(0.07)) }
                // Kontrol ettir: a slow light from the right edge until this viewer opens the card.
                if let glow = CheckRequests.shared.glow(reminder, me: RealAuth.shared.account?.uid, categoryColor: reminder.customCategoryId == nil ? nil : category.map { $0.visual.stripe.0 }) { CheckGlowOverlay(color: glow) }
                if let stripes {
                    HStack(spacing: 3.5) {
                        Rectangle().fill(stripes.0).frame(width: 5)
                        Rectangle().fill(stripes.1).frame(width: 5)
                    }
                    .padding(.trailing, 9)
                }
            }
            .clipShape(shape)
        }
        .overlay {
            shape.strokeBorder(borderColor, lineWidth: (isSelected || reminder.isImportant) ? 1.25 : 0.75)
        }
        .contentShape(shape)
        .onTapGesture(perform: onOpen)
        .onLongPressGesture(minimumDuration: 0.4) {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onLongPress()
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private var borderColor: Color {
        if isSelected { return RealColors.accentBlue.opacity(0.65) }
        if reminder.isImportant { return Color.white.opacity(0.85) }
        return accent != nil ? Color.white.opacity(0.10) : RealColors.cardBorder
    }

    private var completionCircle: some View {
        Button {
            if reminder.hasRepeat {
                recurringAck = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { recurringAck = false }
            }
            if !reminder.isCompleted { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            onToggle()
        } label: {
            ZStack {
                Circle()
                    .fill(reminder.isCompleted ? (useDark ? Color(argb: 0xFF202025) : RealColors.textMuted)
                          : (recurringAck ? RealColors.accentBlue.opacity(0.25) : .clear))
                Circle()
                    .strokeBorder(reminder.isCompleted ? .clear : (recurringAck ? RealColors.accentBlue
                                  : (useDark ? Color(argb: 0xFF35353C) : Color(argb: 0xFF626269))), lineWidth: 1.5)
                if reminder.isCompleted || recurringAck {
                    ActionIcon(.check, size: 13)
                        .foregroundStyle(recurringAck && !reminder.isCompleted ? RealColors.accentBlue
                                         : (useDark ? .white : Color(argb: 0xFF1B1B1F)))
                }
            }
            .frame(width: 20, height: 20)
            .scaleEffect(recurringAck ? 1.05 : 1)
            .animation(.spring(duration: 0.2), value: reminder.isCompleted)
            .frame(width: 48, height: 48)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(reminder.isCompleted ? "Tamamlamayı geri al" : "Tamamla")
    }

    private var selectionDot: some View {
        ZStack {
            Circle().fill(isSelected ? RealColors.accentBlue : .clear)
            Circle().strokeBorder(isSelected ? RealColors.accentBlue : .white.opacity(0.3), lineWidth: 1.5)
            if isSelected {
                RealSymbol("checkmark", size: 11.5).foregroundStyle(Color(argb: 0xFF0F172A))
            }
        }
        .frame(width: 22, height: 22)
    }

    @ViewBuilder private var meta: some View {
        if journalListMode, let mood = reminder.journal?.mood {
            HStack(spacing: 6) {
                Text(mood.emoji).font(.system(size: 13))
                Text(mood.label)
            }
            .font(RealType.cardMetaPrimary)
            .foregroundStyle(subtext)
            .padding(.top, 4)
        }
        if journalListMode, let text = reminder.journal?.text, !text.isEmpty {
            Text(text).font(RealFont.inter(14)).foregroundStyle(subtext).lineLimit(3).padding(.top, 4)
        }
        if !journalListMode { standardMeta }
    }

    @ViewBuilder private var standardMeta: some View {
        if let journal = reminder.journal {
            HStack(spacing: 4) {
                Text("Günlük")
                if let mood = journal.mood { Text(mood.emoji).font(.system(size: 10)) }
            }
            .font(RealType.cardMetaPrimary)
            .foregroundStyle(subtext)
            .padding(.top, 3)
        }
        // One quiet line (ReminderCard.kt): date · place (grey pin, or the Varınca / Ayrılınca arrow) · ✓ 2/5 for a
        // checklist. Photos, voice and stacks stay on the detail page.
        let trigger = reminder.isCompleted ? nil : reminder.locationTrigger
        let plainPlace: String? = (trigger == nil && !reminder.isCompleted && reminder.location?.latitude != nil)
            ? reminder.location.map { $0.placeName.trimmingCharacters(in: .whitespaces) }.flatMap { $0.isEmpty ? nil : $0 } : nil
        let checklist = reminder.isChecklist && !reminder.checklistItems.isEmpty ? reminder.checklistItems : nil
        let dated = !reminder.isCompleted && (reminder.alertAt != nil || reminder.plannedDateEpochDay != nil)
        if dated || trigger != nil || plainPlace != nil || checklist != nil {
            HStack(spacing: 5) {
                if dated, let alert = reminder.alertAt {
                    let overdue = alert < EpochMillis.now
                    let color: Color = useDark ? (overdue ? Color(argb: 0xFFC53030) : Color(argb: 0xFF1E3A8A))
                        : (overdue ? RealColors.overdue : RealColors.accentBlue)
                    Text(reminder.hasRepeat ? "\(RealDates.reminder(alert)) • \(Recurrence.patternLabel(reminder) ?? reminder.repeatRule.displayName)"
                         : RealDates.reminder(alert))
                        .foregroundStyle(color).lineLimit(1).layoutPriority(1)
                } else if dated, let day = reminder.plannedDateEpochDay {
                    Text(RealDates.planned(day))
                        .foregroundStyle(useDark ? Color(argb: 0xFF1E3A8A) : RealColors.accentBlue).lineLimit(1).layoutPriority(1)
                }
                if let trigger {
                    PlaceTriggerGlyph(onExit: trigger.onExit).frame(width: 12, height: 12)
                } else if plainPlace != nil {
                    RealSymbol("mappin", size: 11).foregroundStyle(subtext)
                }
                if let name = trigger.map(\.placeName).flatMap({ $0.isEmpty ? nil : $0 }) ?? plainPlace {
                    Text(name).foregroundStyle(subtext).lineLimit(1).truncationMode(.tail)
                }
                if let checklist {
                    if dated || trigger != nil || plainPlace != nil { Text("·").foregroundStyle(subtext) }
                    RealSymbol("checklist", size: 11).foregroundStyle(subtext)
                    Text("\(checklist.filter(\.isCompleted).count)/\(checklist.count)").foregroundStyle(subtext).lineLimit(1).layoutPriority(1)
                }
            }
            .font(RealType.cardMeta)
            .padding(.top, 5)
        }
        if let done = reminder.completedAt {
            Text("Tamamlandı: \(RealDates.reminder(done))")
                .font(RealType.cardMeta)
                .foregroundStyle(useDark ? Color(argb: 0xFF55555E) : Color(argb: 0xFF92929A))
                .padding(.top, 5)
        }
        if categoryLabel, reminder.customCategoryId != nil, let category {
            // The category colour, lifted a little so dark ones stay readable on the card.
            CategoryPill(text: category.name, foreground: useDark ? textColor : category.visual.stripe.0.lerp(.white, 0.3))
                .frame(maxWidth: 200, alignment: .leading)
                .padding(.top, 8)
        }
    }

    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: 18)
            .onChanged { value in
                guard abs(value.translation.width) > abs(value.translation.height) else { return }
                if dragX == 0 { swipeBegan(at: value.startLocation.x) }
                swipeChanged(value.translation.width)
            }
            .onEnded { value in
                // A vertical scroll never moved the card: nothing to settle.
                guard dragX != 0 else { return }
                swipeEnded(value.translation.width, predicted: value.predictedEndTranslation.width)
            }
    }

    private func swipeBegan(at x: CGFloat) {
        let at = x / cardWidth
        mayLeft = swipeLeftOn && at >= 0.6
        mayRight = swipeRightOn && at <= 0.4
    }

    private func swipeChanged(_ w: CGFloat) {
        let clamped = min(max(w, mayLeft ? -.infinity : 0), mayRight ? .infinity : 0)
        if abs(clamped) >= 82 && abs(dragX) < 82 { UISelectionFeedbackGenerator().selectionChanged() }
        dragX = clamped
    }

    private func swipeEnded(_ w: CGFloat, predicted: CGFloat) {
        let left = dragX < 0
        let commit = abs(dragX) >= 82 || (abs(predicted) > 300 && (predicted < 0) == left && abs(dragX) > 28)
        if commit && !left && swipeRightKeepsCard {
            // Move to 24 hour keeps the card here: act, then it slides back in (no bounce).
            onSwipeTo24Hour?()
            withAnimation(.timingCurve(0.2, 0, 0, 1, duration: 0.32)) { dragX = 0 }
            return
        }
        withAnimation(.spring(duration: 0.3)) { dragX = commit ? (left ? -900 : 900) : 0 }
        if commit {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.14) {
                if left { onSwipeDelete?() } else { onSwipeTo24Hour?() }
                dragX = 0
            }
        }
    }
}

/// Sideways pan for list cards (iOS 18 UIGestureRecognizerRepresentable). It begins only when the finger moves more
/// sideways than up/down; otherwise it fails at once and the scroll view keeps the touch.
@available(iOS 18.0, *)
struct HorizontalSwipeGesture: UIGestureRecognizerRepresentable {
    var enabled: Bool
    /// Where the finger went down, from the card's left edge.
    var onBegan: (CGFloat) -> Void
    var onChanged: (CGFloat) -> Void
    /// Translation and the predicted end translation (as SwiftUI's DragGesture gives it).
    var onEnded: (CGFloat, CGFloat) -> Void

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var enabled = true
        func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
            guard enabled, let pan = g as? UIPanGestureRecognizer, let view = pan.view else { return false }
            let v = pan.velocity(in: view)
            return abs(v.x) > abs(v.y) * 1.2
        }
    }

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator { Coordinator() }

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let pan = UIPanGestureRecognizer()
        pan.delegate = context.coordinator
        context.coordinator.enabled = enabled
        return pan
    }

    func updateUIGestureRecognizer(_ recognizer: UIPanGestureRecognizer, context: Context) {
        context.coordinator.enabled = enabled
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        guard let view = recognizer.view else { return }
        let t = recognizer.translation(in: view).x
        switch recognizer.state {
        case .began:
            onBegan(recognizer.location(in: view).x - t)
            onChanged(t)
        case .changed: onChanged(t)
        case .ended: onEnded(t, t + recognizer.velocity(in: view).x * 0.25)
        case .cancelled, .failed: onEnded(t, t)
        default: break
        }
    }
}

/// Light from the edge a swiped card leaves (as on the posters): red on the right (Sil), blue on the left.
private struct CardSwipeGlow: View {
    let x: CGFloat
    var body: some View {
        let p = min(abs(x) / 82, 1.4) / 1.4
        let s = p * p * (3 - 2 * p)
        let color = x < 0 ? Color(argb: 0xFFFF4D6D) : Color(argb: 0xFF4D8DFF)
        let armed: Double = abs(x) >= 82 ? 1.35 : 1
        GeometryReader { geo in
            let radius = max(abs(x) * 1.15, geo.size.height * 0.8)
            ZStack(alignment: x < 0 ? .trailing : .leading) {
                RadialGradient(colors: [color.opacity(min(0.42 * s * armed, 0.6)), color.opacity(0.16 * s * armed), .clear],
                               center: x < 0 ? .trailing : .leading, startRadius: 0, endRadius: radius)
                LinearGradient(colors: [.clear, color.opacity(0.75 * s), .white.opacity(0.9 * s), color.opacity(0.75 * s), .clear],
                               startPoint: .top, endPoint: .bottom)
                    .frame(width: 2.5)
            }
        }
        .allowsHitTesting(false)
    }
}

struct CompactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon.font(.system(size: 10))
            configuration.title
        }
    }
}

/// Category deck tile (CollectionCategoryCard).
struct CategoryTileView: View {
    let visual: CategoryVisual
    let count: Int
    var description: String = ""
    var showCount = true
    var hero = false
    var extendUnderNext = false
    var isSelected = false
    /// Home deck while categories are being picked (long press on a custom category, as on Android).
    var selectionMode = false
    /// Hero tile on the category screen: progress ring + sort arrow next to the stacked pills (CategoryHeroActions).
    var heroActions: AnyView? = nil

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: hero ? 30 : 26, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center) {
                Text(visual.title)
                    .font(hero ? RealType.categoryTitleHero : RealType.categoryTitle)
                    .foregroundStyle(visual.foreground)
                    .lineLimit(hero ? 3 : 2)
                    .offset(x: 10)
                Spacer(minLength: 12)
                if hero && heroActions != nil {
                    RealSymbol(visual.symbol, size: 23).foregroundStyle(visual.foreground).frame(width: 48, height: 48)
                } else {
                    Text("↗").font(RealFont.inter(28)).foregroundStyle(visual.foreground)
                }
            }
            Spacer().frame(height: hero ? 12 : 14)
            if hero, let heroActions {
                HStack(alignment: .center, spacing: 8) {
                    VStack(alignment: .leading, spacing: 8) {
                        if showCount { CategoryPill(text: "\(count) kart", foreground: visual.foreground) }
                        if !description.isEmpty { CategoryPill(text: description, foreground: visual.foreground).frame(maxWidth: 190, alignment: .leading) }
                    }
                    Spacer()
                    heroActions
                }
            } else {
                HStack(spacing: 8) {
                    if showCount { CategoryPill(text: "\(count) kart", foreground: visual.foreground) }
                    if !description.isEmpty {
                        CategoryPill(text: description, foreground: visual.foreground).frame(maxWidth: 190, alignment: .leading)
                    }
                    Spacer()
                    if selectionMode {
                        Text(isSelected ? "✓ Seçili" : "○ Seç").font(RealFont.inter(13, .medium)).foregroundStyle(visual.foreground)
                    }
                    RealSymbol(visual.symbol, size: 21.8).foregroundStyle(visual.foreground)
                }
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, hero ? 26 : 16)
        .padding(.bottom, hero ? 36 : 46 + (extendUnderNext ? 40 : 0))
        .frame(maxWidth: .infinity, minHeight: hero ? 156 : 108, alignment: .topLeading)
        .background(shape.fill(Color(argb: 0xFF14151C)).overlay(shape.fill(visual.gradient)))
        .overlay(shape.strokeBorder(isSelected ? Color(argb: 0xFF94B8F7).opacity(0.85) : .white.opacity(0.2), lineWidth: isSelected ? 2 : 0.75))
        .clipShape(shape)
    }
}

/// CategoryPill: outlined capsule, 1pt border at 42% of the foreground.
struct CategoryPill: View {
    let text: String
    let foreground: Color

    var body: some View {
        Text(text)
            .font(RealType.categoryPill)
            .foregroundStyle(foreground)
            .lineLimit(1)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .overlay(Capsule().strokeBorder(foreground.opacity(0.42), lineWidth: 1))
    }
}

/// Varınca: a navigation arrow pointing in (green); Ayrılınca: the same arrow turned around (amber).
struct PlaceTriggerGlyph: View {
    let onExit: Bool
    static func tone(_ onExit: Bool) -> Color { onExit ? Color(argb: 0xFFFFB86F) : Color(argb: 0xFF5DCAA5) }

    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            var arrow = Path()
            arrow.move(to: CGPoint(x: w * 0.92, y: h * 0.08))
            arrow.addLine(to: CGPoint(x: w * 0.08, y: h * 0.45))
            arrow.addLine(to: CGPoint(x: w * 0.48, y: h * 0.52))
            arrow.addLine(to: CGPoint(x: w * 0.55, y: h * 0.92))
            arrow.closeSubpath()
            ctx.fill(arrow, with: .color(Self.tone(onExit)))
        }
        .rotationEffect(.degrees(onExit ? 180 : 0))
        .accessibilityLabel(onExit ? "Ayrılınca hatırlatır" : "Varınca hatırlatır")
    }
}
