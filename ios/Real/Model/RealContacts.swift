import Foundation
import Observation

/// A person in Kişilerim as this device knows them (RealContacts.kt): a connection's nickname (`uid` set, "Neşe Gül" →
/// "annem") or someone without REAL (`uid` nil), made by hand or by picking a typed name. Kept on the device.
struct RealContact: Codable, Hashable, Identifiable {
    var id: String
    var name: String
    var nickname: String? = nil
    var uid: String? = nil
}

@Observable
final class RealContacts {
    static let shared = RealContacts()
    static let defaultsKey = "real_contacts"
    private static let trLocale = Locale(identifier: "tr_TR")

    private let defaults: UserDefaults
    private(set) var list: [RealContact] = []

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        self.list = Self.read(from: defaults)
    }

    private static func key(_ s: String) -> String { s.lowercased(with: trLocale) }

    /// The name came from typing: the person is made the first time, once.
    func addTyped(_ name: String) {
        let target = Self.key(name)
        guard !list.contains(where: { c in
            Self.key(c.name) == target || (c.nickname.map { Self.key($0) == target } ?? false)
        }) else { return }
        write(list + [RealContact(id: UUID().uuidString.lowercased(), name: name)])
    }

    /// A connection's nickname; blank takes it off.
    func setNickname(uid: String, name: String, nickname: String) {
        let rest = list.filter { $0.uid != uid }
        let clean = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        write(clean.isEmpty ? rest : rest + [RealContact(id: uid, name: name, nickname: clean, uid: uid)])
    }

    /// Adds or edits someone without REAL (`id` nil = new).
    func saveLocal(id: String?, name: String, nickname: String) {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNick = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        let contact = RealContact(id: id ?? UUID().uuidString.lowercased(),
                                  name: cleanName,
                                  nickname: cleanNick.isEmpty ? nil : cleanNick,
                                  uid: nil)
        write(list.filter { $0.id != contact.id } + [contact])
    }

    func delete(id: String) {
        write(list.filter { $0.id != id })
    }

    private func write(_ contacts: [RealContact]) {
        list = contacts
        if let data = try? JSONEncoder().encode(contacts) {
            defaults.set(data, forKey: Self.defaultsKey)
        }
    }

    private static func read(from defaults: UserDefaults) -> [RealContact] {
        let data: Data?
        if let d = defaults.data(forKey: defaultsKey) {
            data = d
        } else if let s = defaults.string(forKey: defaultsKey) {
            data = s.data(using: .utf8)
        } else {
            data = nil
        }
        guard let data, let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else {
            return []
        }
        return arr.compactMap { o in
            guard let id = (o["id"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !id.isEmpty,
                  let name = (o["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else {
                return nil
            }
            let nickname = (o["nickname"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let uid = (o["uid"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            return RealContact(id: id,
                               name: name,
                               nickname: (nickname?.isEmpty == false) ? nickname : nil,
                               uid: (uid?.isEmpty == false) ? uid : nil)
        }
    }
}
