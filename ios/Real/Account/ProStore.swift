import Foundation
import Observation
import StoreKit

/// App Store subscriptions for REAL Pro. Apple takes the payment; the worker reads the purchase from Apple and
/// answers with the account's access, so being Pro is always the server's word (ProAccess), never this phone's.
@MainActor
@Observable
final class ProStore {
    static let shared = ProStore()

    enum Plan: String { case monthly, yearly, founder }
    struct Offer: Identifiable {
        let plan: Plan
        /// Nil only on the demo screen (CI screenshots), which has nothing to buy.
        let product: Product?
        let id: String
        /// The App Store's price ("₺79,99"), and for a yearly plan what that is per month.
        let price: String
        let perMonth: String?

        init(plan: Plan, product: Product) {
            self.plan = plan
            self.product = product
            id = product.id
            price = product.displayPrice
            perMonth = plan == .monthly ? nil : (product.price / 12).formatted(product.priceFormatStyle)
        }

        init(demo plan: Plan, price: String, perMonth: String?) {
            self.plan = plan
            product = nil
            id = "demo." + plan.rawValue
            self.price = price
            self.perMonth = perMonth
        }
    }

    /// What can be bought now, best offer first. The founder plan is here only while the server says places are left.
    private(set) var offers: [Offer] = []
    private(set) var founderLeft = 0
    /// AI requests a Pro account gets per day (the server's number), for the "Neler dahil?" sheet.
    private(set) var proDailyAi: Int?
    private(set) var loading = false
    private(set) var loaded = false
    private(set) var working = false
    /// CI screenshots (-REALDemo -REALScreen pro): the plans with their App Store prices, without a store or an account.
    private(set) var demo = false
    var notice: String?

    func showDemo() {
        demo = true
        loaded = true
        founderLeft = 100
        proDailyAi = 100
        offers = [Offer(demo: .founder, price: "$19.99", perMonth: "$1.67"),
                  Offer(demo: .yearly, price: "$29.99", perMonth: "$2.50"),
                  Offer(demo: .monthly, price: "$3.99", perMonth: nil)]
    }

    @ObservationIgnored private var token: UUID?
    @ObservationIgnored private var listener: Task<Void, Never>?

    /// App start and every sign-in: renewals and refunds that arrive while the app runs, and an entitlement this
    /// phone holds that the account has not been given yet (bought offline, new phone).
    func start() {
        if listener == nil {
            listener = Task { [weak self] in
                for await result in StoreKit.Transaction.updates {
                    guard case .verified(let transaction) = result else { continue }
                    await self?.arrived(transaction)
                }
            }
        }
        Task { await sync() }
    }

    func reset() {
        offers = []
        loaded = false
        notice = nil
        token = nil
    }

    private func arrived(_ transaction: StoreKit.Transaction) async {
        guard let uid = RealAuth.shared.account?.uid else { return }
        if (try? await claim(transaction.id, uid: uid)) != nil { await transaction.finish() }
    }

    private func sync() async {
        guard let uid = RealAuth.shared.account?.uid else { return }
        for await result in StoreKit.Transaction.currentEntitlements {
            guard case .verified(let transaction) = result, transaction.productType == .autoRenewable else { continue }
            if UserDefaults.standard.string(forKey: Self.claimedKey(uid)) == String(transaction.id) { continue }
            _ = try? await claim(transaction.id, uid: uid)
        }
    }

    private static func claimedKey(_ uid: String) -> String { "pro_claimed_v1/" + uid }

    func load() async {
        guard !demo, !loading, let uid = RealAuth.shared.account?.uid else { return }
        loading = true
        defer { loading = false; loaded = true }
        do {
            let data = try await RealBackend.request("/api/access/v1/store", timeout: 15)
            guard uid == RealAuth.shared.account?.uid,
                  let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let store = root["store"] as? [String: Any] else { return }
            proDailyAi = (store["proDailyAi"] as? NSNumber)?.intValue
            guard store["enabled"] as? Bool == true, let ids = store["products"] as? [String: String] else { offers = []; return }
            let founder = store["founder"] as? [String: Any]
            let open = founder?["open"] as? Bool == true
            founderLeft = max(0, ((founder?["limit"] as? NSNumber)?.intValue ?? 0) - ((founder?["taken"] as? NSNumber)?.intValue ?? 0))
            token = (store["accountToken"] as? String).flatMap { UUID(uuidString: $0) }
            let products = try await Product.products(for: Array(ids.values))
            offers = [Plan.founder, .yearly, .monthly].compactMap { plan in
                guard plan != .founder || open, let id = ids[plan.rawValue],
                      let product = products.first(where: { $0.id == id }) else { return nil }
                return Offer(plan: plan, product: product)
            }
        } catch {
            notice = message(error)
        }
    }

    func buy(_ offer: Offer) async {
        guard !working, let product = offer.product, let uid = RealAuth.shared.account?.uid else { return }
        working = true
        notice = nil
        defer { working = false }
        do {
            var options: Set<Product.PurchaseOption> = []
            // Ties the purchase to this REAL account, so no other account can claim it.
            if let token { options.insert(.appAccountToken(token)) }
            switch try await product.purchase(options: options) {
            case .success(let result):
                guard case .verified(let transaction) = result else { notice = t("Satın alma doğrulanamadı."); return }
                do {
                    try await claim(transaction.id, uid: uid)
                    await transaction.finish()
                } catch {
                    // Paid, but the server could not be told. The transaction stays unfinished and is sent again.
                    let code = (error as? RealBackend.Failure)?.code
                    notice = code == "PURCHASE_OTHER_ACCOUNT" ? message(error)
                        : t("Ödemen alındı. REAL Pro, bağlantı kurulunca kendiliğinden açılır.")
                }
            case .pending:
                notice = t("Satın alma onay bekliyor. Onaylanınca REAL Pro açılır.")
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            notice = message(error)
        }
    }

    func restore() async {
        guard !working, let uid = RealAuth.shared.account?.uid else { return }
        working = true
        notice = nil
        defer { working = false }
        do {
            try await AppStore.sync()
            var found = false
            for await result in StoreKit.Transaction.currentEntitlements {
                guard case .verified(let transaction) = result, transaction.productType == .autoRenewable else { continue }
                found = true
                try await claim(transaction.id, uid: uid)
            }
            if !found { notice = t("Bu Apple hesabında etkin bir abonelik bulunamadı.") }
        } catch {
            notice = message(error)
        }
    }

    private func claim(_ id: UInt64, uid: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["transactionId": String(id)])
        _ = try await RealBackend.request("/api/access/v1/purchases/apple", method: "POST", body: body, timeout: 20)
        UserDefaults.standard.set(String(id), forKey: Self.claimedKey(uid))
        guard uid == RealAuth.shared.account?.uid else { return }
        _ = try? await ProAccess.shared.refresh()
    }

    private func message(_ error: Error) -> String? {
        if let store = error as? StoreKitError, case .userCancelled = store { return nil }
        if let failure = error as? RealBackend.Failure {
            switch failure.code ?? "" {
            case "PURCHASE_OTHER_ACCOUNT": return t("Bu abonelik başka bir REAL hesabına bağlı.")
            case "PURCHASE_NOT_FOUND", "PURCHASE_NOT_VALID": return t("Satın alma doğrulanamadı.")
            case "STORE_UNAVAILABLE": return t("App Store'a şu anda ulaşılamıyor. Biraz sonra tekrar dene.")
            default: break
            }
        }
        if error is StoreKitError || error is Product.PurchaseError { return t("Satın alma tamamlanamadı. Biraz sonra tekrar dene.") }
        return RealBackend.message(for: error)
    }
}
