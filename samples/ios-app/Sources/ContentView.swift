import DynamicSDK
import DynamicSdkBusinessAccount
import SwiftUI

struct ContentView: View {
    @EnvironmentObject var store: Store

    var body: some View {
        Group {
            switch store.screen {
            case .splash: SplashView()
            case .login: LoginView()
            case let .otp(uuid, email): OtpView(uuid: uuid, email: email)
            case .home: HomeView()
            case let .walletDetails(wallet): WalletDetailsView(wallet: wallet)
            case let .sign(wallet): SignView(wallet: wallet)
            case let .signTypedData(wallet): SignTypedDataView(wallet: wallet)
            case let .signTransaction(wallet): SignTransactionView(wallet: wallet)
            case let .send(wallet): SendView(wallet: wallet)
            case let .exportKey(wallet): ExportKeyView(wallet: wallet)
            case let .walletPassword(wallet): WalletPasswordView(wallet: wallet)
            case .profile: ProfileView()
            case .createWallet: CreateWalletView()
            case .importPrivateKey: ImportPrivateKeyView()
            case .passkeys: PasskeysView()
            case .mfaRecoveryCodes: MfaRecoveryCodesView()
            case .stepUpEntry: StepUpEntryView()
            case .businessAccounts: BusinessAccountsListView()
            case let .businessAccountDetail(account): BusinessAccountDetailView(account: account)
            case .developerSettings: DeveloperSettingsView()
            }
        }
        .background(AppColors.bgGrey)
        .overlay(alignment: .bottom) {
            if let toast = store.toast {
                Text(toast)
                    .appBodyMedium(color: .white)
                    .padding(12)
                    .background(AppColors.txtPrimary.opacity(0.9))
                    .cornerRadius(8)
                    .padding()
                    .onAppear {
                        Task { try? await Task.sleep(nanoseconds: 3_000_000_000); store.toast = nil }
                    }
            }
        }
        // Step-up gate, driven by Store.ensureStepUp — same modal across
        // every screen that signs/sends, mirroring the other 3 platforms.
        // .sheet(...) is its own presentation container, not a descendant
        // of WindowGroup's view for color-scheme-inheritance purposes on
        // iOS 15 — .preferredColorScheme(.light) is repeated here so the
        // forced-light rule can't regress on this one screen.
        .sheet(item: $store.stepUpRequest) { request in
            StepUpView(request: request)
                .preferredColorScheme(.light)
        }
    }
}

private struct SplashView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        // Full-screen LoginBackground (Assets.xcassets, pulled from the
        // Dynamic Flutter SDK example app's assets/splash.png) — matches its
        // own SplashScreen, which is JUST this image; the progress indicator
        // is this demo's own addition on top of it.
        ZStack {
            Image("LoginBackground")
                .resizable()
                .aspectRatio(contentMode: .fill)
                .ignoresSafeArea()
            ProgressView()
        }
        .task { await store.boot() }
    }
}

/// Hidden Developer Settings gesture: 7 taps on the logo within 2 seconds —
/// same mechanism as Flutter's SecretTapDetector/MAUI's SecretTapDetector,
/// reimplemented with a tap-count @State + a rolling Date window instead of
/// a shared detector class (SwiftUI has no direct gesture-recognizer
/// equivalent to reach for here).
private struct SecretTapModifier: ViewModifier {
    let requiredTaps: Int
    let window: TimeInterval
    let onUnlocked: () -> Void
    @State private var tapCount = 0
    @State private var firstTapAt: Date?

    func body(content: Content) -> some View {
        content.onTapGesture {
            let now = Date()
            if let firstTapAt, now.timeIntervalSince(firstTapAt) > window {
                tapCount = 0
                self.firstTapAt = nil
            }
            if firstTapAt == nil { firstTapAt = now }
            tapCount += 1
            if tapCount >= requiredTaps {
                tapCount = 0
                firstTapAt = nil
                onUnlocked()
            }
        }
    }
}

private extension View {
    func secretTap(taps: Int = 7, within window: TimeInterval = 2, action: @escaping () -> Void) -> some View {
        modifier(SecretTapModifier(requiredTaps: taps, window: window, onUnlocked: action))
    }
}

/// The wallets this demo offers, so each one can be tested by hand. Mirrored
/// in the Android, Flutter and MAUI demos.
///
/// REGISTRY KEYS, not names, and that matters: a name match picks the wrong
/// wallet here. "Trust" also matches `ta` (TrustAssetApp) and "Bitget" also
/// matches `bgw`, whose link is a Telegram bot, not the app.
///
/// Most connect over WalletConnect: each has sign_v2 and a mobile deep link,
/// so the tap opens that wallet straight away (see
/// ExternalWalletOption.connectionDeeplink).
///
/// Phantom and Solflare have NEITHER, and go through the shared Solana
/// deep-link mechanism instead, which builds its own URL — installed in
/// DynamicDemoApp.swift, which also owns the onOpenURL that carries their
/// answers back. See SolanaDeepLinkWallet for the five values that differ
/// between the two.
///
/// Xverse IS listed. ExternalWalletProviderAdapter now proposes bip122
/// (Bitcoin) too — see docs/external-wallets.md's "Bitcoin over
/// WalletConnect" section for the shapes. Verified end to end only on
/// Kotlin/Android so far, against a real device; this platform's connect
/// and signature path are unverified (neither confirmed nor ruled out)
/// against reown-swift.
/// Coinbase uses its popup web view because its registry entry supports
/// WalletConnect sign_v1 only.
private let testWalletKeys: Set<String> = [
    "metamask",
    "phantom",
    "solflare",
    "trust",
    "okxwallet",
    "rabby",
    "rainbow",
    "backpack",
    "bitgetwallet",
    "exodus",
    "zerion",
    "xverse",
    "coinbasewallet",
]

private struct LoginView: View {
    @EnvironmentObject var store: Store
    @State private var email = ""
    @State private var externalJwt = ""
    @State private var showWalletPicker = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Spacer(minLength: 24)
                // The real Dynamic wordmark (Assets.xcassets, pulled from the
                // Dynamic Flutter SDK example app's assets/dynamic-logo.png),
                // same 200x100 sizing the reference uses. Tapping it 7x
                // within 2s opens the hidden Developer Settings screen.
                Image("DynamicLogo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(height: 100)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                    .secretTap { store.screen = .developerSettings }
                Text("Headless Login").appTitleMedium()
                Text("Email").appBodySmall()
                TextField("you@example.com", text: $email)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                Button("Send code") {
                    Task { await store.sendOtp(email.trimmingCharacters(in: .whitespaces)) }
                }
                .buttonStyle(AppPrimaryButtonStyle())

                if !store.socialProviders.isEmpty {
                    orDivider
                    ForEach(store.socialProviders, id: \.providerKind) { provider in
                        Button {
                            Task { await store.signInWithSocial(provider.providerKind) }
                        } label: {
                            HStack(spacing: Spacing.space8) {
                                if provider.providerKind == "google" {
                                    Image("GoogleLogo").resizable().frame(width: 18, height: 18)
                                }
                                Text(socialLabel(provider.providerKind)).appBodyLarge()
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(AppPrimaryButtonStyle())
                    }
                }

                // External wallet (MetaMask, ...) over WalletConnect. The
                // picker comes from the SDK's own wallet registry; picking one
                // opens THAT wallet, while "Other wallet" leaves the choice to
                // the OS.
                orDivider
                Button {
                    Task {
                        await store.loadExternalWallets()
                        if store.externalWalletOptions.isEmpty {
                            await store.signInWithExternalWallet(walletKey: nil)
                        } else {
                            showWalletPicker = true
                        }
                    }
                } label: {
                    HStack(spacing: Spacing.space8) {
                        Image(systemName: "wallet.pass")
                        Text("Continue with a wallet").appBodyLarge()
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(AppPrimaryButtonStyle())

                orDivider
                Button {
                    Task { await store.signInWithPasskey() }
                } label: {
                    HStack(spacing: Spacing.space8) {
                        Image(systemName: "person.badge.key")
                        Text("Sign in with passkey").appBodyLarge()
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(AppPrimaryButtonStyle())

                // BYOA (bring-your-own-auth): exchange a JWT the HOST app's
                // own identity provider issued for a Dynamic session,
                // instead of Dynamic owning the credential. No
                // externalUserId field — the JWT's `sub` claim IS the
                // external user id (signInWithExternalJwt's own doc).
                orDivider
                Text("External JWT").appBodySmall()
                TextField("Paste a JWT from your own identity provider", text: $externalJwt)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                Button("Sign in with external JWT") {
                    Task { await store.signInWithExternalJwt(externalJwt.trimmingCharacters(in: .whitespaces)) }
                }
                .buttonStyle(AppPrimaryButtonStyle())
                Spacer(minLength: 24)
            }
            .padding(24)
        }
        .disabled(store.busy)
        .sheet(isPresented: $showWalletPicker) {
            NavigationView {
                List {
                    // A real picker shows the head of the registry above a
                    // search field. This demo shows a FIXED shortlist
                    // instead, so every wallet worth testing by hand is one
                    // tap away: the registry order buries Exodus at 154 and
                    // Zerion at 509, and any "top N" cut hides them.
                    ForEach(
                        store.externalWalletOptions.filter { testWalletKeys.contains($0.key) },
                        id: \.key
                    ) { option in
                        Button {
                            showWalletPicker = false
                            Task { await store.signInWithExternalWallet(walletKey: option.key) }
                        } label: {
                            VStack(alignment: .leading) {
                                Text(option.name).appBodyLarge()
                                Text(option.chain).appBodySmall()
                            }
                        }
                    }
                    Button("Other wallet") {
                        showWalletPicker = false
                        Task { await store.signInWithExternalWallet(walletKey: nil) }
                    }
                }
                .navigationTitle("Connect a wallet")
            }
        }
    }

    private var orDivider: some View {
        HStack(spacing: Spacing.space8) {
            Rectangle().fill(AppColors.bgBase4).frame(height: 1)
            Text("OR").appBodySmall()
            Rectangle().fill(AppColors.bgBase4).frame(height: 1)
        }
    }

    private func socialLabel(_ providerKind: String) -> String {
        "Continue with \(providerKind.prefix(1).uppercased() + providerKind.dropFirst())"
    }
}

private struct OtpView: View {
    @EnvironmentObject var store: Store
    let uuid: String
    let email: String
    @State private var code = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Enter the code sent to").appBodyMedium()
            Text(email).appListItemTitle()
            TextField("OTP code", text: $code)
                .textFieldStyle(.roundedBorder)
                .keyboardType(.numberPad)
            Button("Verify") {
                Task { await store.verifyOtp(code.trimmingCharacters(in: .whitespaces), uuid: uuid) }
            }
            .buttonStyle(AppPrimaryButtonStyle())
        }
        .padding(24)
    }
}
