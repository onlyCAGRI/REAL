import SwiftUI

/// VoiceListeningWindow.kt: a liquid-glass window that replaces the composer while dictating. The glass turns blue
/// with the microphone level; committed words are white, the live partial is soft blue. While finishing, a REAL-blue
/// band sweeps right → left through the glass.
struct VoiceListeningWindow: View {
    let voice: VoiceInput
    let onMic: () -> Void

    private let blue = Color(argb: 0xFF69ACFF)
    private let blueSoft = Color(argb: 0xFF91B9FF)
    private let blueDeep = Color(argb: 0xFF2D6FD6)
    private let ink = Color(argb: 0xFFF7F7F8)

    var body: some View {
        let listening = voice.phase == .listening
        let processing = voice.phase == .finishing
        let level = Double(min(max(voice.level, 0), 1))
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        HStack(alignment: .bottom, spacing: 6) {
            VStack(alignment: .leading, spacing: 4) {
                if !processing {
                    Text(t("Dinleniyor…")).font(RealFont.inter(12, .medium)).foregroundStyle(blueSoft.opacity(0.9))
                }
                // Grows with the words, up to a few lines; never the whole screen (the composer area is unbounded).
                transcript
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button(action: onMic) {
                ActionIcon(.mic, color: blue, size: processing ? 20 : 24)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(blueDeep.opacity(0.30 + level * 0.28)))
                    .scaleEffect(1 + level * 0.14)
                    .frame(width: 48, height: 48)
                    .opacity(processing ? 0.6 : 1)
            }
            .buttonStyle(.plain)
            .disabled(!listening)
            .accessibilityLabel(listening ? t("Dinlemeyi bitir") : t("REAL düşünüyor"))
        }
        .padding(.leading, 22).padding(.trailing, 9).padding(.top, 16).padding(.bottom, 8)
        .frame(maxWidth: .infinity, minHeight: 118, maxHeight: 230, alignment: .bottom)
        .background {
            ZStack {
                shape.fill(Color.black.opacity(0.26))
                if processing {
                    TimelineView(.animation) { tl in
                        RealSweepBand(progress: (tl.date.timeIntervalSinceReferenceDate / 1.4).truncatingRemainder(dividingBy: 1))
                    }
                } else {
                    GeometryReader { g in
                        RadialGradient(colors: [blueDeep.opacity(0.10 + level * 0.34), .clear], center: UnitPoint(x: 0.86, y: 0.95),
                                       startRadius: 0, endRadius: g.size.width * (0.55 + level * 0.30))
                        LinearGradient(colors: [blue.opacity(0.03 + level * 0.10), .clear], startPoint: .top, endPoint: .bottom)
                    }
                    .animation(.linear(duration: 0.11), value: level)
                }
            }
            .clipShape(shape)
        }
        .liquidGlass(shape)
        .transition(.opacity.combined(with: .offset(y: 12)))
    }

    @ViewBuilder
    private var transcript: some View {
        let committed = voice.committed
        let partial = voice.partial
        if committed.isEmpty && partial.isEmpty {
            Text(t("Aklında ne var? Konuş…")).font(RealFont.inter(16)).foregroundStyle(Color(argb: 0xFFA2C7FF)).lineSpacing(6)
        } else {
            (Text(committed).foregroundColor(ink) + Text(committed.isEmpty || partial.isEmpty ? "" : " ") + Text(partial).foregroundColor(blueSoft))
                .font(RealFont.inter(16)).lineSpacing(6).lineLimit(5)
        }
    }
}
