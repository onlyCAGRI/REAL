import SwiftUI

/// Mood kaydı (interim): pick a mood, scope and energy. The full Mood V2 flow (face, slider, descriptors,
/// factors) replaces this next.
struct MoodAddSheet: View {
    @Environment(LifeStore.self) private var life
    @Environment(\.dismiss) private var dismiss
    @State private var mood: JournalMood?
    @State private var scope: MoodScope = .daily
    @State private var energy: MoodEnergy?

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Bugünün havası nasıldı?").font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
            HStack(spacing: 8) {
                ForEach([MoodScope.daily, .moment], id: \.self) { s in
                    Button { scope = s } label: {
                        Text(s.label).font(RealFont.inter(14, .medium)).foregroundStyle(scope == s ? .black : .white)
                            .padding(.horizontal, 14).frame(height: 36)
                            .background(Capsule().fill(scope == s ? Color.white : Color.white.opacity(0.08)))
                    }
                }
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(JournalMood.allCases) { m in
                    Button { mood = m } label: {
                        VStack(spacing: 6) {
                            Text(m.emoji).font(.system(size: 30))
                            Text(m.label).font(RealFont.inter(12)).foregroundStyle(.white).lineLimit(1)
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(RoundedRectangle(cornerRadius: 18).fill(mood == m ? YasamTone.mood(m).opacity(0.35) : Color.white.opacity(0.05)))
                        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(mood == m ? YasamTone.mood(m) : .clear, lineWidth: 1.5))
                    }
                }
            }
            HStack(spacing: 8) {
                Text("Enerji").font(RealFont.inter(13)).foregroundStyle(YasamTone.muted)
                ForEach(MoodEnergy.allCases, id: \.self) { e in
                    Button { energy = energy == e ? nil : e } label: {
                        Text(e.label).font(RealFont.inter(13, .medium)).foregroundStyle(energy == e ? .black : .white)
                            .padding(.horizontal, 12).frame(height: 32)
                            .background(Capsule().fill(energy == e ? Color.white : Color.white.opacity(0.08)))
                    }
                }
            }
            Button {
                if let mood { life.addMood(mood, details: MoodDetails(scope: scope, energy: energy, loggedAt: .now)) }
                dismiss()
            } label: {
                Text("Kaydet").font(RealFont.inter(17, .semibold)).foregroundStyle(.black)
                    .frame(maxWidth: .infinity, minHeight: 52).background(Capsule().fill(mood == nil ? Color.white.opacity(0.4) : .white))
            }
            .disabled(mood == nil)
        }
        .padding(22)
        .presentationDetents([.large])
        .presentationBackground(.ultraThinMaterial)
    }
}
