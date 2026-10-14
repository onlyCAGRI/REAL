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

    @State private var dragX: CGFloat = 0
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
            if swipeEnabled && swipeRightOn {
                Text(swipeRightLabel)
                    .font(RealFont.inter(12, .semibold))
                    .foregroundStyle(RealColors.selection)
                    .padding(.leading, 18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .opacity(Double(min(max(dragX / 82, 0), 1)))
            }
            if swipeEnabled && swipeLeftOn {
                Text("Sil")
                    .font(RealFont.inter(12, .semibold))
                    .foregroundStyle(Color(argb: 0xFFFF8A80))
                    .padding(.trailing, 18)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .opacity(Double(min(max(-dragX / 82, 0), 1)))
            }
            card
                .offset(x: dragX)
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
        if reminder.isChecklist && !reminder.checklistItems.isEmpty {
            HStack(spacing: 18) {
                Text("\(reminder.checklistItems.count) madde")
                Text("\(reminder.checklistItems.filter(\.isCompleted).count) tamamlandı")
            }
            .font(RealType.cardMeta)
            .foregroundStyle(subtext)
            .padding(.top, 4)
        }
        if let journal = reminder.journal {
            HStack(spacing: 4) {
                Text("Günlük")
                if let mood = journal.mood { Text(mood.emoji).font(.system(size: 10)) }
            }
            .font(RealType.cardMetaPrimary)
            .foregroundStyle(subtext)
            .padding(.top, 3)
        }
        if !reminder.attachments.isEmpty || reminder.location != nil || reminder.cardStack != nil {
            HStack(spacing: 10) {
                if !reminder.attachments.isEmpty { Label("\(reminder.attachments.count)", systemImage: "photo") }
                if let location = reminder.location { Label(location.placeName, systemImage: "mappin").lineLimit(1) }
                if let stack = reminder.cardStack, !stack.cards.isEmpty { Label("\(stack.cards.count) kart", systemImage: "rectangle.stack") }
            }
            .labelStyle(CompactLabelStyle())
            .font(RealType.cardMeta)
            .foregroundStyle(subtext)
            .padding(.top, 5)
        }
        if !reminder.isCompleted {
            if let alert = reminder.alertAt {
                let overdue = alert < EpochMillis.now
                let color: Color = useDark ? (overdue ? Color(argb: 0xFFC53030) : Color(argb: 0xFF1E3A8A))
                    : (overdue ? RealColors.overdue : RealColors.accentBlue)
                Text(reminder.hasRepeat ? "\(RealDates.reminder(alert)) • \(Recurrence.patternLabel(reminder) ?? reminder.repeatRule.displayName)"
                     : RealDates.reminder(alert))
                    .font(RealType.cardMeta)
                    .foregroundStyle(color)
                    .padding(.top, 5)
            } else if let day = reminder.plannedDateEpochDay {
                Text(RealDates.planned(day))
                    .font(RealType.cardMeta)
                    .foregroundStyle(useDark ? Color(argb: 0xFF1E3A8A) : RealColors.accentBlue)
                    .padding(.top, 5)
            }
        }
        if let done = reminder.completedAt {
            Text("Tamamlandı: \(RealDates.reminder(done))")
                .font(RealType.cardMeta)
                .foregroundStyle(useDark ? Color(argb: 0xFF55555E) : Color(argb: 0xFF92929A))
                .padding(.top, 5)
        }
    }

    private var swipeGesture: some Gesture {
        DragGesture(minimumDistance: 18)
            .onChanged { value in
                guard abs(value.translation.width) > abs(value.translation.height) else { return }
                let w = value.translation.width
                let clamped = min(max(w, swipeLeftOn ? -.infinity : 0), swipeRightOn ? .infinity : 0)
                if abs(clamped) >= 82 && abs(dragX) < 82 { UISelectionFeedbackGenerator().selectionChanged() }
                dragX = clamped
            }
            .onEnded { value in
                let left = dragX < 0
                let predicted = value.predictedEndTranslation.width
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
                    RealSymbol(visual.symbol, size: 21.8).foregroundStyle(visual.foreground)
                }
            }
        }
        .padding(.horizontal, 22)
        .padding(.top, hero ? 26 : 16)
        .padding(.bottom, hero ? 36 : 46 + (extendUnderNext ? 40 : 0))
        .frame(maxWidth: .infinity, minHeight: hero ? 156 : 108, alignment: .topLeading)
        .background(shape.fill(Color(argb: 0xFF14151C)).overlay(shape.fill(visual.gradient)))
        .overlay(shape.strokeBorder(isSelected ? RealColors.accentBlue.opacity(0.85) : .white.opacity(0.2), lineWidth: isSelected ? 2 : 0.75))
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
