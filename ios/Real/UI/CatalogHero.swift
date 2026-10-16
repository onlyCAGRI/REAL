import SwiftUI

/// Catalog block as the card's cover ("En üstteki içeriği büyüt", CatalogHero.kt): the poster / artwork on top over its
/// own blurred light, reaching up under the status bar (the back / star buttons float over the top corners), then the
/// title, the details and `content` (controls, "…'de aç" with the source at the bottom right).
struct CatalogHeroCover<Content: View>: View {
    let imageURL: URL?
    let posterAspect: CGFloat
    let title: String
    var subtitle: String? = nil
    var description: String? = nil
    var placeholderSymbol = "photo"
    let inset: CGFloat
    @ViewBuilder var content: () -> Content
    @Environment(\.cardBlockHeight) private var total
    @State private var ambient: Color?

    private var base: Color { ambient.map { Color(argb: 0xFF111114).lerp($0, 0.34) } ?? Color(argb: 0xFF16161A) }

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { geo in
                let areaH = max(geo.size.height - inset - 14, 60)
                let w = min(areaH * posterAspect, geo.size.width - 64)
                ZStack {
                    // The same picture, large and soft, lights the cover behind the poster.
                    AsyncImage(url: imageURL) { phase in
                        if let image = phase.image { image.resizable().scaledToFill() } else { Color.clear }
                    }
                    .frame(width: geo.size.width, height: geo.size.height).clipped()
                    .blur(radius: 36).opacity(0.55)
                    LinearGradient(stops: [.init(color: .black.opacity(0.35), location: 0), .init(color: .clear, location: 0.55),
                                           .init(color: base, location: 1)], startPoint: .top, endPoint: .bottom)
                    ZStack {
                        Color(argb: 0xFF26262C)
                        RealSymbol(placeholderSymbol, size: 30).foregroundStyle(Color(argb: 0xFFB8B8C2))
                        AsyncImage(url: imageURL) { phase in
                            if let image = phase.image { image.resizable().scaledToFill() } else { Color.clear }
                        }
                    }
                    .frame(width: w, height: w / posterAspect)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .shadow(color: .black.opacity(0.45), radius: 18, y: 8)
                    .position(x: geo.size.width / 2, y: inset + 8 + areaH / 2)
                }
            }
            // The picture gets most of the cover (the text below adds its own height), never a thumbnail.
            .frame(height: total > 0 ? max(total * 0.66, 340) : 340)
            VStack(alignment: .leading, spacing: 5) {
                Text(title).font(RealFont.inter(26, .semibold)).foregroundStyle(Color(argb: 0xFFF4F4F6)).lineLimit(2)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle).font(RealFont.inter(14)).foregroundStyle(Color(argb: 0xFFB8B8C2)).lineLimit(1)
                }
                if let description, !description.isEmpty {
                    Text(description).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFF8E8E98)).lineSpacing(3).lineLimit(3)
                }
                Color.clear.frame(height: 6)
                content()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 22).padding(.top, 12).padding(.bottom, 18)
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .frame(minHeight: total > 0 ? total : nil)
        .background(LinearGradient(colors: [base, Color(argb: 0xFF0D0D0F)], startPoint: .top, endPoint: .bottom))
        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28, style: .continuous))
        .task(id: imageURL) {
            guard let imageURL, let (data, _) = try? await URLSession.shared.data(from: imageURL),
                  let img = UIImage(data: data), let c = CoverPalette.dominant(img) else { return }
            withAnimation(.easeOut(duration: 0.3)) { ambient = c }
        }
    }
}

/// "…'de aç" as a wide pill with the source quietly at the bottom right.
struct HeroOpenRow<Source: View>: View {
    let label: String
    let action: () -> Void
    @ViewBuilder var source: () -> Source

    var body: some View {
        HStack(spacing: 12) {
            Button(action: action) {
                Text(label).font(RealFont.inter(14, .medium)).foregroundStyle(Color(argb: 0xFFE2E2E8))
                    .frame(maxWidth: .infinity).frame(height: 46)
                    .background(Capsule().fill(Color.white.opacity(0.10)))
            }
            .buttonStyle(.plain)
            source()
        }
    }
}
