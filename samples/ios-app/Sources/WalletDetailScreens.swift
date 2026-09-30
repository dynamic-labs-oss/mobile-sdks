import DynamicSDK
import SwiftUI

// Wallet details — chain/type card, full address, per-chain action section
// (mirrors the reference's `_NetworkActions`/equivalent, branching by
// wallet.chain), and entry points into Sign/Sign Typed Data/Sign
// Transaction/Send/Export/Wallet Password. Mirrors wallet_details_screen.dart
// / WalletDetailsPage.cs / the Kotlin WalletDetailsView composable.
struct WalletDetailsView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var balance: String?
    @State private var multichainBalances: [(name: String, balance: String)]?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button("← Back") { store.screen = .home }.buttonStyle(AppTextButtonStyle())
                    Text("Wallet Details").appSectionHeading()
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Chain: \(wallet.chain)").appBodyMedium()
                    Text(wallet.isEmbedded ? "Type: Embedded (WaaS)" : "Type: External wallet").appBodySmall()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(AppColors.bgWhite)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
                .cornerRadius(10)
                Text("Address:").appListItemTitle()
                Text(wallet.address).appBodyMedium()
                if store.hasChainExtension(wallet.chain) {
                    Text("Balance: \(balance ?? "loading…")").appBodyMedium()
                }
                // EVM/Solana/Sui network switcher — moved here from Home,
                // scoped to this wallet's own chain family. NOTE the
                // underlying selection is still client-wide
                // (setActiveEvmChainId/setActiveSolanaNetwork/
                // setActiveSuiNetwork have exactly one value each, shared by
                // every wallet of that chain family — see
                // wallets.flows.ts/state.ts), so this fixes PLACEMENT, not
                // independence; flagged via the caption below rather than
                // implied away. BTC has no headless active-network switch at
                // all (see BtcExtension's doc) so it's just shown as static text.
                networkSection
                if store.hasChainExtension(wallet.chain) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Balances on all networks:").appListItemTitle()
                        // No getMultichainBalance primitive exists in the SDK —
                        // Store.multichainBalances(chain:address:) is a demo-app
                        // -side loop over each chain's configured networks,
                        // fetched concurrently and caught per-network.
                        if let multichainBalances {
                            ForEach(multichainBalances, id: \.name) { entry in
                                Text("\(entry.name): \(entry.balance)").appBodyMedium()
                            }
                        } else {
                            Text("loading…").appBodySmall()
                        }
                    }
                }
                Button("Copy Address") {
                    UIPasteboard.general.string = wallet.address
                    store.toast = "Copied to clipboard"
                }
                .buttonStyle(AppOutlinedButtonStyle())
                .frame(maxWidth: .infinity)
                actionSection
                Button("Wallet Password") { store.screen = .walletPassword(wallet: wallet) }
                    .buttonStyle(AppOutlinedButtonStyle())
                    .frame(maxWidth: .infinity)
                Button("Export Private Key") {
                    store.screen = .exportKey(wallet: wallet)
                }
                .buttonStyle(AppOutlinedButtonStyle())
                .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
        .background(AppColors.bgGrey)
        .task(id: wallet.address) {
            guard store.hasChainExtension(wallet.chain) else { return }
            balance = await store.balance(chain: wallet.chain, address: wallet.address)
            multichainBalances = await store.multichainBalances(chain: wallet.chain, address: wallet.address)
        }
    }

    @ViewBuilder
    private var networkSection: some View {
        switch wallet.chain {
        case "EVM":
            VStack(alignment: .leading, spacing: 4) {
                Text("Network:").appListItemTitle()
                Picker("EVM network", selection: Binding(
                    get: { store.activeEvmChainId ?? store.evmNetworks.first!.chainId },
                    set: { store.switchEvmNetwork($0) }
                )) {
                    ForEach(store.evmNetworks, id: \.chainId) { network in
                        Text(network.name).tag(network.chainId)
                    }
                }
                .pickerStyle(.menu)
                Text("Applies to all EVM wallets on this client.").appBodySmall()
            }
        case "SOL":
            VStack(alignment: .leading, spacing: 4) {
                Text("Network:").appListItemTitle()
                Picker("Solana network", selection: Binding(
                    get: { store.activeSolanaNetwork ?? store.solanaNetworks.first!.name },
                    set: { store.switchSolanaNetwork($0) }
                )) {
                    ForEach(store.solanaNetworks, id: \.name) { network in
                        Text(network.name).tag(network.name)
                    }
                }
                .pickerStyle(.menu)
                Text("Applies to all Solana wallets on this client.").appBodySmall()
            }
        case "SUI":
            VStack(alignment: .leading, spacing: 4) {
                Text("Network:").appListItemTitle()
                Picker("Sui network", selection: Binding(
                    get: { store.activeSuiNetwork ?? store.suiNetworks.first!.name },
                    set: { store.switchSuiNetwork($0) }
                )) {
                    ForEach(store.suiNetworks, id: \.name) { network in
                        Text(network.name).tag(network.name)
                    }
                }
                .pickerStyle(.menu)
                Text("Applies to all Sui wallets on this client.").appBodySmall()
            }
        case "BTC":
            VStack(alignment: .leading, spacing: 4) {
                Text("Network:").appListItemTitle()
                Text(store.btcNetworks.first!.name).appBodyMedium()
                Text("BTC has no headless network switch — see BtcExtension's doc.").appBodySmall()
            }
        default:
            EmptyView()
        }
    }

    // Per-chain action section — mirrors the reference's chain-branching
    // action buttons (CAPABILITIES.md): EVM gets Sign Message + Sign Typed
    // Data; Solana gets just Sign Message; Sui gets Sign Message + Sign
    // Transaction; BTC gets Sign Message + Sign PSBT (both the generic
    // signTransaction screen, labeled per chain). Send/"Send Bitcoin" is
    // offered for every chain with a chain extension.
    @ViewBuilder
    private var actionSection: some View {
        Button("Sign Message") { store.screen = .sign(wallet: wallet) }
            .buttonStyle(AppOutlinedButtonStyle())
            .frame(maxWidth: .infinity)
        if wallet.chain == "EVM" {
            Button("Sign Typed Data") { store.screen = .signTypedData(wallet: wallet) }
                .buttonStyle(AppOutlinedButtonStyle())
                .frame(maxWidth: .infinity)
        }
        if wallet.chain == "SUI" {
            Button("Sign Transaction") { store.screen = .signTransaction(wallet: wallet) }
                .buttonStyle(AppOutlinedButtonStyle())
                .frame(maxWidth: .infinity)
        }
        if wallet.chain == "BTC" {
            Button("Sign PSBT") { store.screen = .signTransaction(wallet: wallet) }
                .buttonStyle(AppOutlinedButtonStyle())
                .frame(maxWidth: .infinity)
        }
        if store.hasChainExtension(wallet.chain) {
            Button(wallet.chain == "BTC" ? "Send Bitcoin" : "Send") {
                store.screen = .send(wallet: wallet)
            }
            .buttonStyle(AppOutlinedButtonStyle())
            .frame(maxWidth: .infinity)
        }
    }
}

/// Create Wallet as its own screen (promoted from Home's inline creation) —
/// chain picker (BTC/EVM/Solana/Sui, Tier-1 everywhere per DESIGN.md/
/// CAPABILITIES.md's consistency fix), a "protect with a password" switch
/// revealing a password + confirm field, submit button in the pill/filled
/// style (DESIGN.md §5's one FilledButton.icon moment). Backs onto
/// waas.createWallet(chain) plus, if the password switch is on,
/// waas.setWaasWalletAccountPassword(...) right after creation.
struct CreateWalletView: View {
    @EnvironmentObject var store: Store
    @State private var chain = "EVM"
    @State private var protectWithPassword = false
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var busy = false
    @State private var error: String?

    private let chains = ["EVM", "SOL", "BTC", "SUI"]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .home }.buttonStyle(AppTextButtonStyle())
                Text("Create Wallet").appSectionHeading()
            }
            Text("Chain").appBodySmall()
            Picker("Chain", selection: $chain) {
                ForEach(chains, id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(.segmented)
            Toggle("Protect with a password", isOn: $protectWithPassword)
                .tint(AppColors.txtLink)
            if protectWithPassword {
                Text("Password").appBodySmall()
                SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
                Text("Confirm password").appBodySmall()
                SecureField("Confirm password", text: $confirmPassword).textFieldStyle(.roundedBorder)
            }
            if let error {
                Text(error).appBodySmall(color: AppColors.txtError)
            }
            Button(busy ? "Creating…" : "Create wallet") {
                Task { await submit() }
            }
            .buttonStyle(AppPillButtonStyle())
            .disabled(busy)
            Spacer()
        }
        .padding(24)
    }

    private func submit() async {
        error = nil
        if protectWithPassword {
            guard !password.isEmpty else { error = "Enter a password."; return }
            guard password == confirmPassword else { error = "Passwords don't match."; return }
        }
        busy = true
        defer { busy = false }
        guard let address = await store.createWallet(chain) else { return }
        guard protectWithPassword else {
            store.screen = .home
            return
        }
        _ = await store.protectNewWallet(chain: chain, address: address, password: password)
        store.screen = .home
    }
}

/// Import Private Key — the mirror image of Create Wallet: same chain
/// picker, a private-key text field instead of "generate", and (SOL/SUI
/// only — ed25519 chains) an isRawScalarImport toggle.
struct ImportPrivateKeyView: View {
    @EnvironmentObject var store: Store
    @State private var chain = "EVM"
    @State private var privateKey = ""
    @State private var isRawScalarImport = false
    @State private var busy = false

    private let chains = ["EVM", "SOL", "BTC", "SUI"]
    private var supportsRawScalarImport: Bool { chain == "SOL" || chain == "SUI" }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .home }.buttonStyle(AppTextButtonStyle())
                Text("Import Private Key").appSectionHeading()
            }
            Text("Chain").appBodySmall()
            Picker("Chain", selection: $chain) {
                ForEach(chains, id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(.segmented)
            Text("Private key").appBodySmall()
            SecureField("Private key", text: $privateKey)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
            if supportsRawScalarImport {
                Toggle("Raw ed25519 scalar (not a seed)", isOn: $isRawScalarImport)
                    .tint(AppColors.txtLink)
            }
            Text(
                "Never share this key. It is never logged, displayed, or stored by this app — " +
                "it is sent once, over your configured apiBaseUrl, into WaaS custody."
            )
            .appBodySmall()
            Button(busy ? "Importing…" : "Import") {
                Task { await submit() }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy || privateKey.isEmpty)
            Spacer()
        }
        .padding(24)
    }

    private func submit() async {
        busy = true
        let key = privateKey
        // Cleared immediately, before the await — this method never logs,
        // toasts, or persists the raw key either (see Store.importPrivateKey's doc).
        privateKey = ""
        _ = await store.importPrivateKey(
            chain: chain, privateKey: key, isRawScalarImport: supportsRawScalarImport ? isRawScalarImport : nil
        )
        busy = false
    }
}
