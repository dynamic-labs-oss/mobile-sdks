import DynamicSDK
import SwiftUI

struct HomeView: View {
    @EnvironmentObject var store: Store
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text(store.user?.email ?? "Signed in").appListItemTitle()
                    Spacer()
                    Button("Profile") { store.screen = .profile }.buttonStyle(AppTextButtonStyle())
                    Button("Sign out") { Task { await store.signOut() } }.buttonStyle(AppTextButtonStyle())
                }
                // EVM/Solana/Sui network switching lives in WalletDetails now,
                // scoped to the wallet you tapped into — not here for the
                // whole app.
                Text("Wallets").appSectionHeading()
                HStack(spacing: Spacing.space8) {
                    Button("Import Private Key") { store.screen = .importPrivateKey }
                        .buttonStyle(AppOutlinedButtonStyle())
                    // Create Wallet is now its own screen (promoted from
                    // inline creation) — DESIGN.md §5's pill/filled style is
                    // reserved for the single primary CTA of a screen; here
                    // that's this button specifically when the wallet list
                    // is empty (the reference's own qualifier for the pill).
                    if store.wallets.isEmpty {
                        Button("+ Create wallet") { store.screen = .createWallet }
                            .buttonStyle(AppPillButtonStyle())
                    } else {
                        Button("+ Create wallet") { store.screen = .createWallet }
                            .buttonStyle(AppOutlinedButtonStyle())
                    }
                }
                if store.wallets.isEmpty {
                    Text("No embedded wallets yet.").appBodySmall()
                } else {
                    ForEach(store.wallets, id: \.id) { wallet in
                        WalletCard(wallet: wallet)
                    }
                }
            }
            .padding(16)
        }
        .background(AppColors.bgGrey)
    }
}

struct WalletCard: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var balance: String?
    private var isPrimary: Bool { wallet.id == store.primaryWalletId }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(isPrimary ? "★ Wallet: \(short(wallet.address)) (primary)" : "Wallet: \(short(wallet.address))")
                .appBodyLarge()
            Text("Chain: \(wallet.chain)").appBodyMedium()
            // Not hardcoded: userWallets lists EXTERNAL wallets too, and each
            // Wallet says which extension signs for it.
            Text(wallet.isEmbedded ? "Type: Embedded (WaaS)" : "Type: External wallet").appBodySmall()
            if store.hasChainExtension(wallet.chain) {
                Text("Balance: \(balance ?? "loading…")").appBodyMedium()
            }
            HStack(spacing: 12) {
                if !isPrimary {
                    // Headless primary-wallet selection (server-recorded);
                    // the ★ badge above updates via primaryWalletIdChanged.
                    Button("Set primary") {
                        Task { await store.setPrimaryWallet(wallet.id) }
                    }.buttonStyle(AppOutlinedButtonStyle())
                }
                Button("Copy") {
                    UIPasteboard.general.string = wallet.address
                    store.toast = "Copied to clipboard"
                }.buttonStyle(AppOutlinedButtonStyle())
                Button("Sign") {
                    store.screen = .sign(wallet: wallet)
                }.buttonStyle(AppOutlinedButtonStyle())
                // All 4 Tier-1 chains (EVM/SOL/BTC/SUI) have a chain
                // extension wired up in this demo — exercise balance + send.
                if store.hasChainExtension(wallet.chain) {
                    Button("Send") {
                        store.screen = .send(wallet: wallet)
                    }.buttonStyle(AppOutlinedButtonStyle())
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(AppColors.bgWhite)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
        .cornerRadius(10)
        .contentShape(Rectangle())
        // Tap the card itself to open Details — no separate "Details" button,
        // matching examples/flutter-app's WalletCard.onPressed (the reference
        // this demo is styled after). Buttons above still consume their own
        // taps first.
        .onTapGesture {
            store.screen = .walletDetails(wallet: wallet)
        }
        // getBalance(address:) accepts any address, not just the caller's own
        // — .task(id:) keys the fetch to the wallet's address so it doesn't
        // re-run on every recomposition (Home rebuilds on userChanged/
        // walletCreated).
        .task(id: wallet.address) {
            if store.hasChainExtension(wallet.chain) {
                balance = await store.balance(chain: wallet.chain, address: wallet.address)
            }
        }
    }

    private func short(_ address: String) -> String {
        address.count > 12 ? "\(address.prefix(6))…\(address.suffix(6))" : address
    }
}

// Profile — raw user JSON (verifiedCredentials included) + the full auth
// JWT (mirror of flutter-sdk/example's ProfileScreen "_valueCard" cards),
// plus entry points into the settings-style screens that don't need their
// own wallet context: Step-Up Auth (proactive), Passkeys, MFA Recovery
// Codes, Business Accounts.
//
// NOTE: the reference also shows a separate "Min Auth Token" — a genuinely
// distinct, shorter token pushed from the SDK's own webview/native bridge
// (auth_module.dart's minifiedTokenChanged store), not a display-side
// truncation of the full JWT. This generated SDK has no equivalent state
// field/bridge message yet, so that card isn't reproduced here — it would
// be new SDK surface (spec + all 4 native bridges), not a UI-only gap. Our
// own authToken is already the JS SDK's PREFERRED minifiedJwt (with a
// fallback to the legacy jwt) per auth.flows.ts — there's no separate
// "full" token being hidden here.
struct ProfileView: View {
    @EnvironmentObject var store: Store

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button("← Back") { store.screen = .home }.buttonStyle(AppTextButtonStyle())
                    Text("Profile").appSectionHeading()
                }
                VStack(spacing: Spacing.space8) {
                    Button("Step-Up Auth") { store.screen = .stepUpEntry }
                        .buttonStyle(AppOutlinedButtonStyle()).frame(maxWidth: .infinity)
                    Button("Passkeys") { store.screen = .passkeys }
                        .buttonStyle(AppOutlinedButtonStyle()).frame(maxWidth: .infinity)
                    Button("MFA Recovery Codes") { store.screen = .mfaRecoveryCodes }
                        .buttonStyle(AppOutlinedButtonStyle()).frame(maxWidth: .infinity)
                    Button("Business Accounts") { store.screen = .businessAccounts }
                        .buttonStyle(AppOutlinedButtonStyle()).frame(maxWidth: .infinity)
                }
                valueCard(title: "User:", value: store.userJson)
                valueCard(title: "Token:", value: store.authTokenValue)
            }
            .padding(24)
        }
        .background(AppColors.bgGrey)
    }

    private func valueCard(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).appListItemTitle()
            ScrollView {
                Text(value).appBodySmall().textSelection(.enabled)
            }
            .frame(maxHeight: 300)
            Button("Copy") {
                UIPasteboard.general.string = value
                store.toast = "Copied to clipboard"
            }.buttonStyle(AppOutlinedButtonStyle())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(AppColors.bgWhite)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
        .cornerRadius(10)
    }
}

// Hidden Developer Settings — reached via the Login logo's 7-tap gesture
// (ContentView.swift's SecretTapModifier). Lets a dev override
// environmentId/apiBaseUrl; the change only takes effect on next launch
// since Store.client is a stored property built once at init (see
// Store.setDeveloperOverrides' doc) — this screen is honest about that
// instead of pretending to hot-swap the live client.
struct DeveloperSettingsView: View {
    @EnvironmentObject var store: Store
    @State private var environmentId = Store.currentEnvironmentId
    @State private var apiBaseUrl = Store.currentApiBaseUrl
    @State private var saved = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .login }.buttonStyle(AppTextButtonStyle())
                Text("Developer Settings").appSectionHeading()
            }
            Text("Environment ID").appBodySmall()
            TextField("environmentId", text: $environmentId)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
            Text("API Base URL").appBodySmall()
            TextField("apiBaseUrl", text: $apiBaseUrl)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
                .keyboardType(.URL)
            Button("Save") {
                Store.setDeveloperOverrides(environmentId: environmentId, apiBaseUrl: apiBaseUrl)
                saved = true
            }
            .buttonStyle(AppPrimaryButtonStyle())
            Button("Reset to default") {
                Store.setDeveloperOverrides(environmentId: nil, apiBaseUrl: nil)
                environmentId = Store.currentEnvironmentId
                apiBaseUrl = Store.currentApiBaseUrl
                saved = true
            }
            .buttonStyle(AppOutlinedButtonStyle())
            if saved {
                Text("Saved. Restart the app to apply the new settings.")
                    .appBodySmall(color: AppColors.green)
            }
            Spacer()
        }
        .padding(24)
    }
}
