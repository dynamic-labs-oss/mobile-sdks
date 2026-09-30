import Combine
import DynamicSDK
import DynamicSdkBtc
import DynamicSdkBusinessAccount
import DynamicSdkEvm
import DynamicSdkEarn
import DynamicSdkExportPrivateKey
import DynamicSdkExternalWallet
import DynamicSdkExternalWalletCoinbase
import DynamicSdkExternalWalletPhantom
import DynamicSdkLegacyWalletUpgrade
import DynamicSdkSolana
import DynamicSdkStellar
import DynamicSdkSui
import DynamicSdkTon
import DynamicSdkWaas
import DynamicSdkZerodev
import Foundation
import SwiftUI

/// Keychain group WalletConnect stores its session keys in. Must match the
/// App Group in project.yml — reown's Networking.configure requires one, and
/// the keychain writes fail without the matching entitlement.
let walletConnectAppGroup = "group.com.dynamic.demo.DynamicDemo"

@main
struct DynamicDemoApp: App {
    @StateObject private var store = Store()
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                // DESIGN.md: "No dark mode. The reference forces light theme
                // only ... Force light appearance on every platform." Forced
                // at the root so no screen can accidentally regress by
                // reaching for an adaptive color again.
                .preferredColorScheme(.light)
                // Where a wallet's answer arrives. Phantom has no open
                // channel to push a reply down — every call is a fresh deep
                // link — so this is what completes a connect or a signature.
                .onOpenURL { store.handleDeepLink($0) }
        }
    }
}

// Carries the real generated Wallet model (client.wallets.userWallets), not
// raw chain/address strings — so Details/Sign/Export call straight through
// to wallet.exportPrivateKey()/wallet.signMessage(...) with no re-lookup by
// address once we're here; the object in hand IS the wallet. Not Equatable:
// Wallet itself isn't (it carries an optional DynamicClient), and nothing
// here relies on Screen equality.
enum Screen {
    case splash
    case login
    case otp(uuid: String, email: String)
    case home
    case walletDetails(wallet: Wallet)
    case sign(wallet: Wallet)
    case signTypedData(wallet: Wallet)
    case signTransaction(wallet: Wallet)
    case send(wallet: Wallet)
    case exportKey(wallet: Wallet)
    case walletPassword(wallet: Wallet)
    case profile
    case createWallet
    case importPrivateKey
    case passkeys
    case mfaRecoveryCodes
    case stepUpEntry
    case businessAccounts
    case businessAccountDetail(account: BusinessAccount)
    case developerSettings
}

/// Scales an on-wire integer-unit balance (wei/lamports/sats/mist) to its
/// human token amount, formatted with its unit name. A free function (not a
/// Store method) so it carries no actor isolation — callable from inside
/// the detached `addTask` closures multichainBalances(chain:address:) fans
/// out to, unlike a local func declared inside a MainActor-isolated method.
private func formattedBalance(_ raw: String, unitsPerToken: Decimal, unit: String) -> String {
    guard let units = Decimal(string: raw) else { return raw }
    let token = units / unitsPerToken
    return "\(NSDecimalNumber(decimal: token).stringValue) \(unit)"
}

/// A pending step-up prompt — set on Store when checkStepUp reports
/// isRequired, driving StepUpView (see ContentView.swift). Generic over
/// whatever credentials the server reports, mirroring the Flutter/Kotlin/
/// MAUI demos: prefers an already-registered TOTP device, falls back to
/// email-OTP reauth when the user has none.
struct StepUpRequest: Identifiable {
    let id = UUID()
    let scope: String
    let credentials: [StepUpCredential]
}

// App-wide holder + view model. Only CONSUMES the SDK — platform-service
// wiring (HttpService, storage, signers, OAuth) lives inside
// DynamicSdk.createDynamicClient; WaaS + the chain extensions are opted into
// explicitly below (WaaS must be installed for any chain to sign — every
// chain extension resolves the WalletSigner capability lazily).
@MainActor
final class Store: ObservableObject {
    // EVM networks the demo supports. The first (Sepolia) is the initial
    // active network; switch at runtime via client.wallets.setActiveEvmChainId.
    private static let evmNetworks = [
        EvmNetwork(chainId: 11155111, rpcUrl: "https://ethereum-sepolia-rpc.publicnode.com", name: "Sepolia"),
        EvmNetwork(chainId: 84532, rpcUrl: "https://sepolia.base.org", name: "Base Sepolia"),
        EvmNetwork(chainId: 1, rpcUrl: "https://ethereum-rpc.publicnode.com", name: "Ethereum"),
        EvmNetwork(chainId: 137, rpcUrl: "https://polygon-bor-rpc.publicnode.com", name: "Polygon"),
    ]

    // Solana networks the demo supports. The first (devnet) is the initial
    // active network; switch at runtime via client.wallets.setActiveSolanaNetwork.
    private static let solanaNetworks = [
        SolanaNetwork(rpcUrl: "https://api.devnet.solana.com", name: "Devnet", cluster: "devnet", genesisHash: "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"),
        SolanaNetwork(rpcUrl: "https://api.mainnet-beta.solana.com", name: "Mainnet Beta", cluster: "mainnet-beta", genesisHash: "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"),
    ]

    // Sui networks the demo supports. The first (testnet) is the initial
    // active network; switch at runtime via client.wallets.setActiveSuiNetwork.
    private static let suiNetworks = [
        SuiNetwork(rpcUrl: "https://graphql.testnet.sui.io/graphql", name: "Testnet", cluster: "testnet"),
        SuiNetwork(rpcUrl: "https://graphql.devnet.sui.io/graphql", name: "Devnet", cluster: "devnet"),
    ]

    // BTC network(s) the demo supports. BtcExtension has no headless
    // active-network switch (see its own doc — BTC mainnet/testnet addresses
    // aren't even the same derivation), so this is deliberately a single
    // testnet entry rather than a picker: no real-value transfers from a demo.
    private static let btcNetworks = [
        BtcNetwork(name: "Testnet", apiBaseUrl: "https://blockstream.info/testnet/api", isTestnet: true),
    ]

    private static let stellarNetworks = [
        StellarNetwork(
            networkPassphrase: "Test SDF Network ; September 2015",
            horizonUrl: "https://horizon-testnet.stellar.org",
            name: "Testnet",
            waasChainId: "2"
        ),
    ]

    private static let tonNetworks = [
        TonNetwork(rpcUrl: "https://testnet.toncenter.com/api/v2/jsonRPC", name: "Testnet", networkGlobalId: -3),
    ]

    private static let weiPerEth = Decimal(sign: .plus, exponent: 18, significand: 1)
    private static let lamportsPerSol = Decimal(sign: .plus, exponent: 9, significand: 1)
    private static let satsPerBtc = Decimal(sign: .plus, exponent: 8, significand: 1)
    private static let mistPerSui = Decimal(sign: .plus, exponent: 9, significand: 1)

    // Developer Settings overrides (hidden screen, reached via the 7-tap
    // logo gesture on Login — see ContentView.swift's SecretTapDetector).
    // Store.client is a stored property built once at init, so a changed
    // override only takes effect on next launch — DeveloperSettingsView's
    // copy says "restart the app to apply" rather than pretending to
    // rebuild the live client in place.
    private static let environmentIdDefaultsKey = "dyn_dev_environmentId"
    private static let apiBaseUrlDefaultsKey = "dyn_dev_apiBaseUrl"
    private static let defaultEnvironmentId = "3e219b76-dcf1-40ab-aad6-652c4dfab4cc"
    private static let defaultApiBaseUrl = "https://app.dynamicauth.com/api/v0"

    static var currentEnvironmentId: String {
        UserDefaults.standard.string(forKey: environmentIdDefaultsKey) ?? defaultEnvironmentId
    }

    static var currentApiBaseUrl: String {
        UserDefaults.standard.string(forKey: apiBaseUrlDefaultsKey) ?? defaultApiBaseUrl
    }

    /// Persists dev overrides for the NEXT launch. Pass nil/empty to reset to the built-in default.
    static func setDeveloperOverrides(environmentId: String?, apiBaseUrl: String?) {
        let defaults = UserDefaults.standard
        if let environmentId, !environmentId.trimmingCharacters(in: .whitespaces).isEmpty {
            defaults.set(environmentId, forKey: environmentIdDefaultsKey)
        } else {
            defaults.removeObject(forKey: environmentIdDefaultsKey)
        }
        if let apiBaseUrl, !apiBaseUrl.trimmingCharacters(in: .whitespaces).isEmpty {
            defaults.set(apiBaseUrl, forKey: apiBaseUrlDefaultsKey)
        } else {
            defaults.removeObject(forKey: apiBaseUrlDefaultsKey)
        }
    }

    /// Retains wallet mechanisms so the app can forward incoming links.
    private static let externalWallets = ExternalWalletProviderAdapter(
        groupIdentifier: walletConnectAppGroup,
        mechanisms: [PhantomMechanism(), SolflareMechanism(), CoinbaseWebviewMechanism()]
    )

    private static let captcha = DynamicSdk.createCaptchaWidget()

    /// Offers an incoming link to the external-wallet mechanisms. Called from
    /// the scene's onOpenURL, which is the only place iOS delivers it.
    ///
    /// A link this does not claim is somebody else's — a social-login
    /// callback arrives on the very same scheme.
    @discardableResult
    func handleDeepLink(_ url: URL) -> Bool {
        Store.externalWallets.handleRedirect(uri: url.absoluteString)
    }

    private let client = DynamicSdk.createDynamicClient(
        environmentId: Store.currentEnvironmentId,
        apiBaseUrl: Store.currentApiBaseUrl,
        appName: "dynamic-ios-demo",
        universalLink: "https://demo.dynamic.xyz",
        nativeLink: "dynamicdemo://"
    )
    .useWaas()
    .useExportPrivateKey()
    // External wallets (MetaMask, ...) over native WalletConnect. No
    // projectId, app name or return links here: the SDK reads the projectId
    // from the environment's settings and the rest from the client above,
    // then hands them to the adapter through initialize(). groupIdentifier
    // stays an argument because it is an entitlement of THIS app (see
    // project.yml), not SDK configuration — WalletConnect keeps its session
    // keys in that keychain group.
    .useExternalWallet(Store.externalWallets)
    .useEvm(evmNetworks)
    .useEarn()
    .useSolana(solanaNetworks)
    .useSui(networks: suiNetworks)
    .useBtc(networks: btcNetworks)
    .useStellar(stellarNetworks)
    .useTon(tonNetworks)
    .useZerodev()
    .useLegacyWalletUpgrade()
    .useBusinessAccount()

    @Published var screen: Screen = .splash
    @Published var user: SdkUser?
    @Published var busy = false
    @Published var toast: String?
    // Drives StepUpView (ContentView.swift) when non-nil; resolved by
    // completeStepUp, which resumes the continuation ensureStepUp is awaiting.
    @Published var stepUpRequest: StepUpRequest?
    private var stepUpContinuation: CheckedContinuation<Bool, Never>?

    // Headless active-network mirrors, kept in sync via the SDK's Combine
    // publishers so Home's/Wallet Details' pickers reflect switches made anywhere.
    @Published var activeEvmChainId: Int64?
    @Published var activeSolanaNetwork: String?
    @Published var activeSuiNetwork: String?
    // Headless primary-wallet mirror — the ★ badge in HomeView's WalletCard
    // updates via client.primaryWalletIdChanged, same pattern as the networks.
    @Published var primaryWalletId: String?

    private var waas: WaasClientImpl {
        get throws { try client.waas }
    }
    var evm: EvmExtension { client.getExtension(EvmExtension.self)! }
    var solana: SolanaExtension { client.getExtension(SolanaExtension.self)! }
    var sui: SuiClientImpl { try! client.sui }
    var btc: BtcClientImpl { try! client.btc }
    private var businessAccount: BusinessAccountClientImpl { try! client.businessAccount }
    var evmNetworks: [EvmNetwork] { Self.evmNetworks }
    var solanaNetworks: [SolanaNetwork] { Self.solanaNetworks }
    var suiNetworks: [SuiNetwork] { Self.suiNetworks }
    var btcNetworks: [BtcNetwork] { Self.btcNetworks }
    private var cancellables = Set<AnyCancellable>()

    init() {
        // The SDK's reactive streams (Combine publishers). userChanged is hot
        // (replays the current value); walletCreated is future-only. Events can
        // fire off-main, so receive(on: main) before touching @Published state.
        client.userChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] user in if let user { self?.user = user } }
            .store(in: &cancellables)
        client.walletCreated
            .receive(on: DispatchQueue.main)
            .sink { [weak self] wallet in self?.toast = "Wallet created: \(wallet.accountAddress)" }
            .store(in: &cancellables)
        // "Go to your wallet." Over WalletConnect a signature request travels
        // down an already-open relay session with no deep link of its own, so
        // without this the app just looks frozen while the wallet waits
        // off-screen.
        client.externalWalletActionRequested
            .receive(on: DispatchQueue.main)
            .sink { [weak self] request in
                let what = request.action == "signTransaction" ? "transaction" : "signature"
                self?.toast = "Open \(request.walletName ?? "your wallet") to approve the \(what)."
            }
            .store(in: &cancellables)
        client.activeEvmChainIdChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] chainId in self?.activeEvmChainId = chainId }
            .store(in: &cancellables)
        client.activeSolanaNetworkNameChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] name in self?.activeSolanaNetwork = name }
            .store(in: &cancellables)
        client.activeSuiNetworkNameChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] name in self?.activeSuiNetwork = name }
            .store(in: &cancellables)
        client.primaryWalletIdChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] id in self?.primaryWalletId = id }
            .store(in: &cancellables)
    }

    // The generated Wallet model, not a hand-rolled row type — each entry
    // already carries signMessage()/exportPrivateKey() bound to this client.
    var wallets: [Wallet] { client.wallets.userWallets }

    // Raw user JSON (verifiedCredentials included) + the full auth JWT —
    // mirror of flutter-sdk/example's ProfileScreen "_valueCard" cards. See
    // ContentView.swift's ProfileView for the "why no Min Auth Token" note.
    var userJson: String {
        guard let user else { return "" }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(user) else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
    var authTokenValue: String { client.auth.authToken ?? "" }

    /// Social login providers enabled for this environment (from project
    /// settings, populated at initialize()). Pass one's providerKind to
    /// signInWithSocial. The raw list (Provider's own doc) is ONE
    /// flat, provider-discriminated array shared by social login, SMS,
    /// ramp, AND account-abstraction (ZeroDev) providers alike — not
    /// social-login-specific — so this filters to entries that are
    /// actually usable as an OAuth social login button: not 'zerodev'
    /// (mirrors getZerodevProviderSettings' own providerKind == 'zerodev'
    /// filter, just inverted) and carrying a baseAuthUrl, which
    /// signInWithSocial needs to build the provider's authorization URL —
    /// an SMS/ramp entry wouldn't have one populated.
    var socialProviders: [Provider] {
        (client.auth.socialProviders ?? []).filter { $0.providerKind != "zerodev" && $0.baseAuthUrl != nil }
    }

    func boot() async {
        do {
            try await client.initialize()
            if client.auth.authToken != nil {
                user = try await client.auth.refreshUser()
                // No explicit auto-create-wallets call needed: WaasExtension
                // subscribes to client.userChanged and syncs missing wallets
                // in the background — mirrors dynamic-auth's
                // useSyncDynamicWaas.
                screen = .home
            } else {
                screen = .login
            }
        } catch {
            toast = "Init failed: \(error)"
            screen = .login
        }
    }

    func sendOtp(_ email: String) async {
        do {
            let sent = try await client.auth.sendEmailOtp(email: email)
            screen = .otp(uuid: sent.verificationUuid, email: sent.email)
        } catch { toast = message(error) }
    }

    func verifyOtp(_ code: String, uuid: String) async {
        do {
            _ = try await client.auth.verifyEmailOtp(verificationUuid: uuid, code: code)
            user = try await client.auth.refreshUser()
            // No explicit auto-create-wallets call needed — see boot()'s comment.
            screen = .home
        } catch { toast = message(error) }
    }

    /// Social login (Login screen's provider buttons) — opens a system
    /// browser auth session via signInWithSocial, mirroring email/OTP's
    /// post-sign-in path (no separate auto-create-wallets call needed here
    /// either).
    func signInWithSocial(_ providerKind: String) async {
        busy = true
        defer { busy = false }
        do {
            user = try await client.auth.signInWithSocial(provider: providerKind)
            screen = .home
        } catch { toast = message(error) }
    }

    /// BYOA (bring-your-own-auth) — exchanges a JWT the HOST app's own
    /// identity provider issued for a Dynamic session, instead of Dynamic
    /// owning the credential. No externalUserId parameter — the JWT's `sub`
    /// claim IS the external user id (signInWithExternalJwt's own doc).
    func signInWithExternalJwt(_ jwt: String) async {
        busy = true
        defer { busy = false }
        do {
            user = try await client.auth.signInWithExternalJwt(jwt: jwt)
            screen = .home
        } catch { toast = message(error) }
    }

    /// The wallets the SDK can offer, loaded for the Login screen's picker.
    /// Empty until loadExternalWallets() runs, and left empty if the registry
    /// cannot be reached — a pairing still works without a picker.
    @Published var externalWalletOptions: [ExternalWalletOption] = []

    func loadExternalWallets() async {
        guard externalWalletOptions.isEmpty else { return }
        externalWalletOptions = (try? await client.externalWallet.listExternalWallets()) ?? []
    }

    /// Sign-in with a wallet the user already has. `walletKey` is the picked
    /// wallet's registry key, or nil for "any wallet": with a key, the handle
    /// carries that wallet's own deep link, so one tap opens the right app
    /// instead of an OS-wide chooser.
    func signInWithExternalWallet(walletKey: String?) async {
        busy = true
        defer { busy = false }
        do {
            // The picked wallet's own chain is narrowed by the provider
            // itself (ExternalWalletOption.chainsForConnect) — this default
            // is only what "any wallet" asks for.
            let handle = try await client.externalWallet.beginExternalWalletConnect(
                chains: ["EVM", "SOL"],
                walletKey: walletKey
            )
            // A null uri means this mechanism needs nothing presented; on iOS
            // there is always a wallet app to hand it to, so it is opened
            // rather than shown as a QR code.
            if let uri = handle.uri, let url = URL(string: uri) {
                await UIApplication.shared.open(url)
            }
            let accounts = try await client.externalWallet.awaitExternalWalletConnection()
            guard let account = accounts.first else {
                toast = "Wallet approved the connection but exposed no accounts"
                return
            }
            user = try await client.externalWallet.signInWithExternalWallet(address: account.address)
            screen = .home
        } catch { toast = message(error) }
    }

    /// Passkey sign-in (Login screen's "Sign in with passkey" button) — a
    /// fresh, UNAUTHENTICATED login via a previously registered passkey (see
    /// registerPasskey, reached post-login from Profile, to add one first).
    /// No manual `user =`/refreshUser() here: signInWithPasskey() already
    /// sets state.user + fires userChanged internally (same as
    /// registerPasskey) — Store's init() subscription picks it up.
    func signInWithPasskey() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await client.auth.signInWithPasskey()
            screen = .home
        } catch { toast = message(error) }
    }

    /// Does NOT navigate — CreateWalletView (the only caller) decides when
    /// to go back to Home, since it may still need to run
    /// protectNewWallet(...) right after this returns (the password switch)
    /// before the screen changes. Returns the created wallet's own
    /// accountAddress (nil on failure) — the caller passes this straight to
    /// protectNewWallet rather than re-deriving "the wallet that was just
    /// created" by guessing from list order/count, which verifiedCredentials
    /// ordering doesn't actually guarantee.
    func createWallet(_ chain: String) async -> String? {
        busy = true
        defer { busy = false }
        do {
            let created = try await waas.createWallet(chain: chain)  // list refresh via onUserChanged
            return created.accountAddress
        } catch {
            toast = message(error)
            return nil
        }
    }

    /// Create Wallet screen's password switch: sets a password on a
    /// just-created wallet. Kept as a separate call (rather than folded into
    /// createWallet) since setWaasWalletAccountPassword needs the created
    /// wallet's own accountAddress.
    func protectNewWallet(chain: String, address: String, password: String) async -> Bool {
        do {
            try await waas.setWaasWalletAccountPassword(chain: chain, accountAddress: address, newPassword: password)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    /// Import Private Key screen — the mirror image of createWallet: same
    /// ceremony, caller-supplied key material instead of generated.
    /// isRawScalarImport only applies to SOL/SUI/TON (ed25519 chains); pass
    /// nil for every other chain (no default on the generated signature).
    /// The caller's TextField is expected to clear itself on return — this
    /// method never logs, toasts, or persists the raw key.
    func importPrivateKey(chain: String, privateKey: String, isRawScalarImport: Bool?) async -> Bool {
        busy = true
        defer { busy = false }
        do {
            _ = try await waas.importPrivateKey(chain: chain, privateKey: privateKey, isRawScalarImport: isRawScalarImport)
            screen = .home
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    // Headless EVM chain switch; the EVM extension re-points its RPC and the
    // Home picker updates via activeEvmChainIdChanged.
    func switchEvmNetwork(_ chainId: Int64) {
        _ = client.wallets.setActiveEvmChainId(chainId: chainId)
    }

    // Headless Solana network switch; same pattern as EVM, keyed by name
    // instead of a numeric chain id (Solana has no such concept).
    func switchSolanaNetwork(_ name: String) {
        _ = client.wallets.setActiveSolanaNetwork(name: name)
    }

    // Headless Sui network switch; same pattern as Solana.
    func switchSuiNetwork(_ name: String) {
        _ = client.wallets.setActiveSuiNetwork(name: name)
    }

    // Headless primary-wallet selection (server-recorded); the ★ badge
    // updates via client.primaryWalletIdChanged.
    func setPrimaryWallet(_ walletId: String) async {
        do {
            _ = try await client.wallets.setPrimaryWallet(walletId: walletId)
        } catch { toast = message(error) }
    }

    private func unitsPerToken(_ chain: String) -> Decimal {
        switch chain {
        case "SOL": return Self.lamportsPerSol
        case "BTC": return Self.satsPerBtc
        case "SUI": return Self.mistPerSui
        default: return Self.weiPerEth
        }
    }

    func unitName(_ chain: String) -> String {
        switch chain {
        case "SOL": return "SOL"
        case "BTC": return "BTC"
        case "SUI": return "SUI"
        default: return "ETH"
        }
    }

    func networkName(_ chain: String) -> String {
        switch chain {
        case "SOL":
            return activeSolanaNetwork ?? Self.solanaNetworks.first!.name
        case "BTC":
            return Self.btcNetworks.first!.name
        case "SUI":
            return activeSuiNetwork ?? Self.suiNetworks.first!.name
        default:
            return Self.evmNetworks.first { $0.chainId == activeEvmChainId }?.name ?? "EVM"
        }
    }

    /// Whether chain has a getBalance/getUtxos-style extension wired up in
    /// this demo (all 4 Tier-1 chains do — see DESIGN.md/CAPABILITIES.md).
    func hasChainExtension(_ chain: String) -> Bool {
        ["EVM", "SOL", "BTC", "SUI"].contains(chain)
    }

    // Balance display: on-wire integer unit (wei / lamports / sats / mist)
    // -> human token amount (ETH / SOL / BTC / SUI), via exact decimal
    // arithmetic (no BigInt on this platform, but demo amounts fit
    // comfortably within Decimal's 38 digits).
    func balance(chain: String, address: String) async -> String {
        do {
            let raw: String
            switch chain {
            case "SOL":
                raw = try await solana.getBalance(address: address)
            case "BTC":
                // A decimal string now, like every other chain's balance.
                raw = try await btc.getBalance(address: address)
            case "SUI":
                raw = try await sui.getBalance(address: address)
            default:
                // evm.getBalance returns BigInt (see EvmExtension.swift); solana's
                // stays String — Solana's getBalance is String in all 4 language
                // SDKs, only EVM's had the Swift-vs-others divergence.
                raw = (try await evm.getBalance(address: address)).description
            }
            guard let units = Decimal(string: raw) else { return raw }
            let token = units / unitsPerToken(chain)
            return "\(NSDecimalNumber(decimal: token).stringValue) \(unitName(chain))"
        } catch {
            return "unavailable (\(error))"
        }
    }

    // No getMultichainBalance primitive exists anywhere in the SDK (spec,
    // generator, or the native chain extension packages) — this loops each
    // chain's configured networks and calls the existing per-call
    // getBalance(address:network:) once per network, concurrently via
    // withThrowingTaskGroup, catching failures individually so one dead RPC
    // doesn't blank the whole list. BTC only ever has one configured
    // network (see btcNetworks' doc) so its list is trivially single-entry.
    func multichainBalances(chain: String, address: String) async -> [(name: String, balance: String)] {
        // `scaled` is a plain top-level (non-actor-isolated) free function —
        // see its own doc — so it's callable from inside these detached
        // addTask closures; a local func declared inline here would inherit
        // Store's MainActor isolation and fail to typecheck when called
        // from a non-isolated Task body.
        let unitsPerToken = unitsPerToken(chain)
        let unit = unitName(chain)
        switch chain {
        case "SOL":
            return await withTaskGroup(of: (String, String).self) { group in
                for network in Self.solanaNetworks {
                    group.addTask {
                        do {
                            let raw = try await self.solana.getBalance(address: address, network: network)
                            return (network.name, formattedBalance(raw, unitsPerToken: unitsPerToken, unit: unit))
                        } catch { return (network.name, "unavailable") }
                    }
                }
                var results: [(String, String)] = []
                for await result in group { results.append(result) }
                return results
            }
        case "SUI":
            return await withTaskGroup(of: (String, String).self) { group in
                for network in Self.suiNetworks {
                    group.addTask {
                        do {
                            let raw = try await self.sui.getBalance(address: address, network: network)
                            return (network.name, formattedBalance(raw, unitsPerToken: unitsPerToken, unit: unit))
                        } catch { return (network.name, "unavailable") }
                    }
                }
                var results: [(String, String)] = []
                for await result in group { results.append(result) }
                return results
            }
        case "BTC":
            return await withTaskGroup(of: (String, String).self) { group in
                for network in Self.btcNetworks {
                    group.addTask {
                        do {
                            let raw = try await self.btc.getBalance(address: address, network: network)
                            return (network.name, formattedBalance(String(raw), unitsPerToken: unitsPerToken, unit: unit))
                        } catch { return (network.name, "unavailable") }
                    }
                }
                var results: [(String, String)] = []
                for await result in group { results.append(result) }
                return results
            }
        default:
            return await withTaskGroup(of: (String, String).self) { group in
                for network in Self.evmNetworks {
                    group.addTask {
                        do {
                            let raw = try await self.evm.getBalance(address: address, network: network).description
                            return (network.name, formattedBalance(raw, unitsPerToken: unitsPerToken, unit: unit))
                        } catch { return (network.name, "unavailable") }
                    }
                }
                var results: [(String, String)] = []
                for await result in group { results.append(result) }
                return results
            }
        }
    }

    // Step-up gate: checkStepUp → StepUpView (via stepUpRequest) to pick a
    // method + validate a code → mints the scope's elevated token. Returns
    // true immediately (no prompt) when the server doesn't require it.
    func ensureStepUp(scope: String) async -> Bool {
        do {
            let check = try await client.auth.checkStepUp(scope: scope)
            if !check.isRequired { return true }
            return await withCheckedContinuation { continuation in
                self.stepUpContinuation = continuation
                self.stepUpRequest = StepUpRequest(scope: scope, credentials: check.credentials)
            }
        } catch {
            toast = message(error)
            return false
        }
    }

    /// Resolves the pending ensureStepUp await — called by StepUpView on verify/cancel.
    func completeStepUp(_ success: Bool) {
        stepUpRequest = nil
        stepUpContinuation?.resume(returning: success)
        stepUpContinuation = nil
    }

    // Thin proxies to client.auth's step-up completion methods (client is
    // private) — called by StepUpView for whichever method it resolved to.
    func sendStepUpEmailOtp() async throws -> SendEmailOtpResult {
        try await client.auth.sendStepUpEmailOtp()
    }

    func completeTotpStepUp(code: String, scope: String) async throws {
        try await client.auth.completeTotpStepUp(code: code, scope: scope)
    }

    func completeEmailOtpStepUp(verificationUuid: String, code: String, scope: String) async throws {
        try await client.auth.completeEmailOtpStepUp(verificationUuid: verificationUuid, code: code, scope: scope)
    }

    // Sends a native-token transfer from an embedded wallet: build + WaaS-sign
    // + broadcast, through evm/solana/sui/btc depending on chain. Amount is
    // entered in the chain's display unit and converted to the on-wire
    // integer unit (wei / lamports / mist / sats) here — mirrors
    // send_screen.dart / SendPage.cs / the Kotlin SendView composable.
    func send(chain: String, address: String, to: String, amountToken: String) async -> String? {
        do {
            guard await ensureStepUp(scope: "wallet:sign") else { return nil }
            guard let amount = Decimal(string: amountToken) else { return nil }
            var scaled = amount * unitsPerToken(chain)
            var rounded = Decimal()
            NSDecimalRound(&rounded, &scaled, 0, .down)
            let units = NSDecimalNumber(decimal: rounded).stringValue
            switch chain {
            case "SOL":
                return try await solana.sendTransaction(request: SolanaTxRequest(from: address, to: to, lamports: units))
            case "BTC":
                guard let sats = Int64(units) else { return nil }
                return try await btc.sendBitcoin(fromAddress: address, recipientAddress: to, amountInSatoshis: sats)
            case "SUI":
                return try await sui.sendTransaction(request: SuiTxRequest(from: address, to: to, mist: units))
            default:
                return try await evm.sendTransaction(request: EvmTxRequest(from: address, to: to, value: units))
            }
        } catch {
            toast = message(error)
            return nil
        }
    }

    func sign(wallet: Wallet, message text: String) async -> String? {
        do {
            guard await ensureStepUp(scope: "wallet:sign") else { return nil }
            return try await wallet.signMessage(message: text)
        } catch {
            toast = message(error)
            return nil
        }
    }

    /// Sign Typed Data (EVM only) — waas.signTypedData isn't a Wallet
    /// model method (unlike signMessage/exportPrivateKey), so this goes
    /// straight through the WaaS client, gated behind the same wallet:sign
    /// scope Sign Message uses.
    func signTypedData(wallet: Wallet, typedData: String) async -> String? {
        do {
            guard await ensureStepUp(scope: "wallet:sign") else { return nil }
            return try await waas.signTypedData(chain: wallet.chain, accountAddress: wallet.address, typedData: typedData)
        } catch {
            toast = message(error)
            return nil
        }
    }

    /// Generic "Sign Transaction" — this demo's answer to Sui's "Sign
    /// Transaction" and BTC's "Sign PSBT" actions (CAPABILITIES.md): the
    /// caller pastes an already-serialized unsigned transaction (unsigned
    /// PSBT base64 for BTC, unsigned TransactionData for Sui/EVM) and this
    /// signs it via the same waas.signTransaction every chain extension's
    /// send path uses internally — it never broadcasts.
    func signTransaction(
        wallet: Wallet, transaction: String, chainId: String?, signingIndexes: [Int]?, allowedSighash: [Int]?
    ) async -> String? {
        do {
            guard await ensureStepUp(scope: "wallet:sign") else { return nil }
            return try await waas.signTransaction(
                chain: wallet.chain, accountAddress: wallet.address, transaction: transaction,
                chainId: chainId, signingIndexes: signingIndexes, allowedSighash: allowedSighash
            )
        } catch {
            toast = message(error)
            return nil
        }
    }

    // ─── Wallet password management ─────────────────────────────────────

    func walletRecoveryState(wallet: Wallet) async -> WalletRecoveryState? {
        do {
            return try await waas.getWalletRecoveryState(chain: wallet.chain, accountAddress: wallet.address)
        } catch {
            toast = message(error)
            return nil
        }
    }

    func unlockWallet(wallet: Wallet, password: String) async -> Bool {
        do {
            _ = try await waas.unlockWallet(chain: wallet.chain, accountAddress: wallet.address, password: password)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func setWalletPassword(wallet: Wallet, newPassword: String) async -> Bool {
        do {
            try await waas.setWaasWalletAccountPassword(chain: wallet.chain, accountAddress: wallet.address, newPassword: newPassword)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func updateWalletPassword(wallet: Wallet, existingPassword: String, newPassword: String) async -> Bool {
        do {
            try await waas.updateWaasPassword(chain: wallet.chain, accountAddress: wallet.address, existingPassword: existingPassword, newPassword: newPassword)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    // ─── Passkeys management ────────────────────────────────────────────

    /// nil on failure (distinct from an empty-but-successful fetch) — so
    /// PasskeysView can tell "no passkeys configured" apart from "couldn't
    /// load passkeys" per DESIGN.md §7's three distinct loading/error/empty states.
    func getPasskeys() async -> [UserPasskey]? {
        do {
            return try await client.auth.getPasskeys()
        } catch {
            toast = message(error)
            return nil
        }
    }

    func registerPasskey() async -> Bool {
        do {
            try await client.auth.registerPasskey()
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func deletePasskey(passkeyId: String) async -> Bool {
        do {
            try await client.auth.deletePasskey(passkeyId: passkeyId)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    // ─── MFA recovery codes ─────────────────────────────────────────────

    var isPendingMfaRecoveryCodesAcknowledgment: Bool {
        client.auth.isPendingMfaRecoveryCodesAcknowledgment
    }

    func getMfaRecoveryCodes() async -> MfaRecoveryCodesResponse? {
        do {
            return try await client.auth.getMfaRecoveryCodes()
        } catch {
            toast = message(error)
            return nil
        }
    }

    func createNewMfaRecoveryCodes() async -> MfaRecoveryCodesResponse? {
        do {
            return try await client.auth.createNewMfaRecoveryCodes()
        } catch {
            toast = message(error)
            return nil
        }
    }

    func acknowledgeMfaRecoveryCodes() async -> Bool {
        do {
            try await client.auth.acknowledgeMfaRecoveryCodes()
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    // ─── Business Accounts ──────────────────────────────────────────────

    /// nil on failure (distinct from an empty-but-successful fetch) — same
    /// reasoning as getPasskeys' doc.
    func listBusinessAccounts() async -> [BusinessAccount]? {
        do {
            return try await businessAccount.listBusinessAccounts().items ?? []
        } catch {
            toast = message(error)
            return nil
        }
    }

    func createBusinessAccount(name: String) async -> Bool {
        do {
            _ = try await businessAccount.createBusinessAccount(name: name, externalRef: nil)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func getBusinessAccount(id: String) async -> BusinessAccountDetail? {
        do {
            return try await businessAccount.getBusinessAccount(businessAccountId: id)
        } catch {
            toast = message(error)
            return nil
        }
    }

    func updateBusinessAccount(id: String, name: String) async -> Bool {
        do {
            _ = try await businessAccount.updateBusinessAccount(businessAccountId: id, name: name)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func addBusinessAccountMember(businessAccountId: String, role: String, identifier: String, identifierType: String) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:member:add") else { return false }
            _ = try await businessAccount.addBusinessAccountMember(
                businessAccountId: businessAccountId,
                targetIdentity: BusinessAccountTargetIdentity(identifier: identifier, identifierType: identifierType),
                role: role
            )
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func updateBusinessAccountMemberRole(businessAccountId: String, userId: String, role: String) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:member:role:update") else { return false }
            _ = try await businessAccount.updateBusinessAccountMemberRole(businessAccountId: businessAccountId, userId: userId, role: role)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func removeBusinessAccountMember(businessAccountId: String, userId: String) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:member:remove") else { return false }
            _ = try await businessAccount.removeBusinessAccountMember(businessAccountId: businessAccountId, userId: userId)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    /// Transfers ownership and clears an ended session.
    func transferBusinessAccountOwnership(businessAccountId: String, newOwnerUserId: String) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:transfer_ownership") else { return false }
            let result = try await businessAccount.transferBusinessAccountOwnership(businessAccountId: businessAccountId, newOwnerUserId: newOwnerUserId)
            if case .actionRequired = result {
                toast = "Approval required"
                return false
            }
            user = nil
            screen = .login
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func linkBusinessAccountWallet(walletId: String, businessAccountId: String?) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:link_wallet") else { return false }
            _ = try await businessAccount.addWalletToBusinessAccount(walletId: walletId, businessAccountId: businessAccountId)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func removeBusinessAccountWallet(businessAccountId: String, walletId: String) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:wallet:remove") else { return false }
            _ = try await businessAccount.removeBusinessAccountWallet(businessAccountId: businessAccountId, walletId: walletId)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func removeBusinessAccountSigner(businessAccountId: String, walletId: String, signerId: String) async -> Bool {
        do {
            guard await ensureStepUp(scope: "business_account:signer:remove") else { return false }
            _ = try await businessAccount.removeBusinessAccountSigner(businessAccountId: businessAccountId, walletId: walletId, signerId: signerId)
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    // Reveals accountAddress's private key directly on screen — never to
    // this app or its developer (see ExportPrivateKeyEngine's docstring).
    // "wallet:export" is a distinct elevated-access scope from Sign/Send's
    // "wallet:sign" — mirrors export_private_key_screen.dart / the Kotlin
    // ExportPrivateKeyView.
    func exportPrivateKey(wallet: Wallet) async -> Bool {
        guard await ensureStepUp(scope: "wallet:export") else { return false }
        do {
            try await wallet.exportPrivateKey()
            return true
        } catch {
            toast = message(error)
            return false
        }
    }

    func signOut() async {
        try? await client.auth.signOut()
        user = nil
        screen = .login
    }

    private func message(_ error: Error) -> String {
        if case let DynamicError.api(_, _, reason, _) = error { return reason }
        if case let DynamicError.network(reason) = error { return reason }
        if case let DynamicError.state(reason) = error { return reason }
        return "\(error)"
    }
}
