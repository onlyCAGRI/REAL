import SwiftUI

/// Ortak kategori → Kişiler (the friend button in the category's ••• menu). The owner adds people from Kişilerim,
/// gives each Tam (add, edit, complete, delete) or Sınırlı (view only) access and can remove them; a member sees who is
/// in it and can leave. Sharing the first time moves the cards to the server behind "Kategori paylaşılıyor"; removing
/// the last person brings them back ("Kategori sana dönüyor").
struct SharedCategorySheet: View {
    let category: CustomCategory
    /// Categories filled from a phone calendar are never shared.
    let calendarLinked: Bool

    @Environment(ReminderStore.self) private var store
    @Environment(LifeStore.self) private var life
    @State private var showPeople = false
    @State private var members: [SharedMember] = []
    @State private var people: [SharedMember] = []
    @State private var error: String?
    @State private var busy = false
    @State private var working: String?
    @State private var moved = 0
    @State private var newAccess = "full"
    @State private var leaving = false

    private var current: CustomCategory { store.category(category.id) ?? category }
    private var isOwner: Bool { current.sharedId == nil || current.sharedRole == "owner" }
    private let sync = SharedCategorySync.shared

    var body: some View {
        ZStack {
            OverlayPanel(onScrimTap: { working != nil }) { _ in content.padding(20) }
            if let working {
                // Like installing an update: nothing else to touch until it is done.
                Color.black.opacity(0.88).ignoresSafeArea()
                VStack(spacing: 14) {
                    ProgressView().tint(.white).scaleEffect(1.3)
                    Text(working).font(RealFont.inter(18, .semibold)).foregroundStyle(.white)
                    if moved > 0 {
                        Text("\(moved)").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.6))
                            .contentTransition(.numericText(value: Double(moved)))
                    }
                }
            }
        }
        .task { await refresh() }
        .sheet(isPresented: $showPeople, onDismiss: { Task { await refresh() } }) { PeopleScreen() }
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 14) {
            Capsule().fill(Color(argb: 0xFF666669)).frame(width: 34, height: 3).frame(maxWidth: .infinity)
            Text("Kişiler").font(RealFont.inter(24, .semibold)).foregroundStyle(.white)
            if calendarLinked && current.sharedId == nil {
                Text("Takvime bağlı kategoriler paylaşılamaz.").font(RealFont.inter(14)).foregroundStyle(.white.opacity(0.6))
            } else {
                if !members.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(members) { m in memberRow(m) }
                    }
                    .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.05)))
                }
                if isOwner {
                    if members.filter({ !$0.isOwner }).count < 10 {
                        HStack {
                            Text("Yeni kişi").font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.6))
                            Spacer()
                            AccessToggle(access: newAccess) { newAccess = $0 }
                        }
                        ScrollView {
                            VStack(spacing: 0) {
                                let candidates = people.filter { p in !members.contains { $0.uid == p.uid } }
                                if candidates.isEmpty {
                                    Text(people.isEmpty ? "Kişilerim boş." : "Herkes bu kategoride.")
                                        .font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.5)).padding(14)
                                    if people.isEmpty {
                                        Button("Kişilerim'i aç") { showPeople = true }
                                            .font(RealFont.inter(13, .semibold)).foregroundStyle(.white).padding(.bottom, 12)
                                    }
                                }
                                ForEach(candidates) { p in
                                    HStack {
                                        ProfileAvatar(name: p.name, size: 36)
                                        Text(p.name).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
                                        Spacer()
                                        Button("Ekle") { add(p.uid) }
                                            .font(RealFont.inter(13, .semibold)).foregroundStyle(.black)
                                            .padding(.horizontal, 14).padding(.vertical, 7).background(Capsule().fill(.white))
                                            .disabled(busy)
                                    }
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                }
                            }
                        }
                        .frame(maxHeight: 260)
                        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.white.opacity(0.05)))
                    } else {
                        Text("Bir kategoride en fazla 10 kişi olabilir.").font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.5))
                    }
                } else if !leaving {
                    Button("Kategoriden ayrıl") { leaving = true }
                        .font(RealFont.inter(15)).foregroundStyle(Color(argb: 0xFFFFB4AB))
                } else {
                    Text("Kartlar kategoride kalır.").font(RealFont.inter(13)).foregroundStyle(.white.opacity(0.6))
                    HStack(spacing: 10) {
                        sheetButton("Kopyasını sakla", filled: true) { act { try await sync.leave(store, category: current, keepCopy: true) } }
                        sheetButton("Benden kaldır", filled: false) { act { try await sync.leave(store, category: current, keepCopy: false) } }
                    }
                }
            }
            if let error { Text(error).font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)) }
        }
    }

    private func memberRow(_ m: SharedMember) -> some View {
        HStack(spacing: 12) {
            ProfileAvatar(name: m.name, size: 38)
            Text(m.name).font(RealFont.inter(15)).foregroundStyle(.white).lineLimit(1)
            Spacer()
            if m.isOwner {
                Text("Sahibi").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.45))
            } else if isOwner {
                AccessToggle(access: m.access) { access in act { try await sync.setAccess(current, uid: m.uid, access: access) } }
                Button("Çıkar") {
                    act {
                        if try await sync.removeMember(current, uid: m.uid) {
                            working = "Kategori sana dönüyor"
                            try await sync.release(store, category: current)
                        }
                    }
                }
                .font(RealFont.inter(13)).foregroundStyle(Color(argb: 0xFFFFB4AB)).padding(.leading, 6)
            } else {
                Text(m.access == "view" ? "Sınırlı" : "Tam").font(RealFont.inter(12)).foregroundStyle(.white.opacity(0.45))
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
    }

    private func refresh() async {
        do {
            members = try await sync.members(current)
            if isOwner { people = try await sync.connections() }
        } catch { self.error = SharedCategorySync.message(error) }
    }

    private func add(_ uid: String) {
        act {
            if current.sharedId == nil {
                working = "Kategori paylaşılıyor"; moved = 0
                // The server only shares categories it already has.
                await RealCloudSync.shared.runFully(store, life)
                try await sync.share(store, category: current, personUid: uid, access: newAccess) { moved = $0 }
            } else {
                try await sync.addMember(current, uid: uid, access: newAccess)
            }
        }
    }

    private func act(_ block: @escaping () async throws -> Void) {
        guard !busy else { return }
        busy = true; error = nil
        Task {
            do { try await block() } catch { self.error = SharedCategorySync.message(error) }
            working = nil
            busy = false
            await refresh()
        }
    }

    private func sheetButton(_ text: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(RealFont.inter(14, .medium)).foregroundStyle(filled ? .black : .white)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(Capsule().fill(filled ? Color.white : Color.white.opacity(0.07)))
        }
        .buttonStyle(.plain).disabled(busy)
    }
}

/// Tam / Sınırlı.
private struct AccessToggle: View {
    let access: String
    let onChange: (String) -> Void
    var body: some View {
        HStack(spacing: 0) {
            ForEach([("full", "Tam"), ("view", "Sınırlı")], id: \.0) { pair in
                let key = pair.0, label = pair.1
                let on = access == key
                Button { if !on { onChange(key) } } label: {
                    Text(label).font(RealFont.inter(12, on ? .semibold : .regular)).foregroundStyle(on ? .black : .white.opacity(0.7))
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Capsule().fill(on ? Color.white : .clear))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(Capsule().fill(.white.opacity(0.06)))
    }
}
