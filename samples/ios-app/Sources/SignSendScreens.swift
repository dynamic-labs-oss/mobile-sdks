import DynamicSDK
import DynamicSdkWaas
import SwiftUI

// Reveals accountAddress's private key directly on screen — never to this
// app or its developer. Gated by a distinct "wallet:export" elevated-access
// scope from Sign/Send's "wallet:sign" (see Store.exportPrivateKey). Mirrors
// export_private_key_screen.dart / ExportPrivateKeyPage.cs / the Kotlin
// ExportPrivateKeyView composable, including its exact disclaimer copy.
struct ExportKeyView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var busy = false
    @State private var done = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .home }.buttonStyle(AppTextButtonStyle())
                Text("Export Private Key").appSectionHeading()
            }
            Text(
                "Your private key gives full control over this wallet. Dynamic reveals it " +
                "directly on screen — never to this app or its developer. Don't share it " +
                "or take a screenshot."
            )
            .appBodyMedium(color: AppColors.txtSecondary)
            Button(busy ? "Revealing…" : "Reveal Private Key") {
                busy = true
                done = false
                Task {
                    done = await store.exportPrivateKey(wallet: wallet)
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy)
            if done {
                Text("Done — the key was shown once and is not stored here.")
                    .appBodySmall()
            }
            Spacer()
        }
        .padding(24)
    }
}

struct SignView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var message = "Hello World"
    @State private var signature: String?
    @State private var busy = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .walletDetails(wallet: wallet) }.buttonStyle(AppTextButtonStyle())
                Text("Sign Message").appSectionHeading()
            }
            TextField("Message", text: $message).textFieldStyle(.roundedBorder)
            Button(busy ? "Signing…" : "Sign") {
                busy = true
                Task {
                    signature = await store.sign(wallet: wallet, message: message)
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy)
            if let signature {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Signed Message").appListItemTitle()
                    Text(signature).appBodySmall()
                }
                .padding(12)
                .background(AppColors.bgWhite)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
                .cornerRadius(10)
            }
            Spacer()
        }
        .padding(24)
    }
}

/// Sign Typed Data (EVM only) — same skeleton as Sign Message, but the
/// message field holds a caller-serialized EIP-712 JSON object and the call
/// goes through waas.signTypedData rather than Wallet.signMessage.
struct SignTypedDataView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var typedData = """
    {"domain":{},"types":{},"primaryType":"","message":{}}
    """
    @State private var signature: String?
    @State private var busy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .walletDetails(wallet: wallet) }.buttonStyle(AppTextButtonStyle())
                Text("Sign Typed Data").appSectionHeading()
            }
            Text("EIP-712 typed data (JSON)").appBodySmall()
            TextEditor(text: $typedData)
                .frame(minHeight: 160)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
            Button(busy ? "Signing…" : "Sign") {
                busy = true
                Task {
                    signature = await store.signTypedData(wallet: wallet, typedData: typedData)
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy)
            if let signature {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Signature").appListItemTitle()
                    Text(signature).appBodySmall()
                }
                .padding(12)
                .background(AppColors.bgWhite)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
                .cornerRadius(10)
            }
            Spacer()
        }
        .padding(24)
    }
}

/// Generic "Sign Transaction" — this demo's answer to Sui's "Sign
/// Transaction" and BTC's "Sign PSBT" actions (CAPABILITIES.md): pastes an
/// already-serialized unsigned transaction (unsigned PSBT base64 for BTC,
/// unsigned TransactionData for Sui/EVM) through waas.signTransaction — it
/// signs but never broadcasts. chainId is only meaningful for EVM;
/// signingIndexes/allowedSighash are only meaningful for BTC's PSBT ceremony
/// (see WalletSigner.signTransaction's doc) — shown only for those chains.
struct SignTransactionView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var transaction = ""
    @State private var chainId = ""
    @State private var signingIndexes = ""
    @State private var allowedSighash = ""
    @State private var signed: String?
    @State private var busy = false

    private var title: String { wallet.chain == "BTC" ? "Sign PSBT" : "Sign Transaction" }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .walletDetails(wallet: wallet) }.buttonStyle(AppTextButtonStyle())
                Text(title).appSectionHeading()
            }
            Text(wallet.chain == "BTC" ? "Unsigned PSBT (base64)" : "Unsigned transaction (serialized)").appBodySmall()
            TextEditor(text: $transaction)
                .frame(minHeight: 140)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
            if wallet.chain == "EVM" {
                Text("Chain ID").appBodySmall()
                TextField("e.g. 11155111", text: $chainId).textFieldStyle(.roundedBorder).keyboardType(.numberPad)
            }
            if wallet.chain == "BTC" {
                Text("Signing indexes (comma-separated, optional)").appBodySmall()
                TextField("0,1", text: $signingIndexes).textFieldStyle(.roundedBorder)
                Text("Allowed sighash flags (comma-separated, optional)").appBodySmall()
                TextField("1", text: $allowedSighash).textFieldStyle(.roundedBorder)
            }
            Button(busy ? "Signing…" : "Sign") {
                busy = true
                Task {
                    signed = await store.signTransaction(
                        wallet: wallet,
                        transaction: transaction,
                        chainId: wallet.chain == "EVM" ? chainId.trimmingCharacters(in: .whitespaces) : nil,
                        signingIndexes: parseInts(signingIndexes),
                        allowedSighash: parseInts(allowedSighash)
                    )
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy || transaction.isEmpty)
            if let signed {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Signed").appListItemTitle()
                    Text(signed).appBodySmall()
                }
                .padding(12)
                .background(AppColors.bgWhite)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
                .cornerRadius(10)
            }
            Spacer()
        }
        .padding(24)
    }

    private func parseInts(_ text: String) -> [Int]? {
        let values = text.split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        return values.isEmpty ? nil : values
    }
}

// Sends a native-token transfer from an embedded wallet: build + WaaS-sign +
// broadcast, through store.evm/solana/sui/btc depending on [chain]. Amounts
// are entered in the chain's display unit (ETH / SOL / BTC / SUI). Mirrors
// send_screen.dart / SendPage.cs / the Kotlin SendView composable.
struct SendView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    let chain: String
    let address: String
    @State private var to = ""
    @State private var amount: String
    @State private var balance: String?
    @State private var txHash: String?
    @State private var busy = false

    init(wallet: Wallet) {
        self.wallet = wallet
        self.chain = wallet.chain
        self.address = wallet.address
        let defaultAmount: String
        switch wallet.chain {
        case "SOL": defaultAmount = "0.01"
        case "BTC": defaultAmount = "0.0001"
        case "SUI": defaultAmount = "0.1"
        default: defaultAmount = "0.001"
        }
        _amount = State(initialValue: defaultAmount)
    }

    private var addressPlaceholder: String {
        switch chain {
        case "SOL": return "To address (base58)"
        case "BTC": return "To address (bech32, bc1…)"
        case "SUI": return "To address (0x…)"
        default: return "To address (0x…)"
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button("← Back") { store.screen = .walletDetails(wallet: wallet) }.buttonStyle(AppTextButtonStyle())
                    Text(chain == "BTC" ? "Send Bitcoin" : "Send (\(store.networkName(chain)))").appSectionHeading()
                }
                Text("From: \(short(address))").appBodyMedium()
                Text("Balance: \(balance ?? "loading…")").appBodyMedium()
                TextField(addressPlaceholder, text: $to)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                Text("Amount (\(store.unitName(chain)))").appBodySmall()
                TextField("Amount", text: $amount)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.decimalPad)
                Button(busy ? "Sending…" : "Send transaction") {
                    busy = true
                    txHash = nil
                    Task {
                        txHash = await store.send(
                            chain: chain, address: address,
                            to: to.trimmingCharacters(in: .whitespaces),
                            amountToken: amount.trimmingCharacters(in: .whitespaces)
                        )
                        busy = false
                    }
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .disabled(busy)
                if let txHash {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Broadcast").appListItemTitle()
                        Text("Tx hash: \(txHash)").appBodySmall()
                        Button("Copy tx hash") {
                            UIPasteboard.general.string = txHash
                            store.toast = "Tx hash copied"
                        }.buttonStyle(AppOutlinedButtonStyle())
                    }
                    .padding(12)
                    .background(AppColors.bgWhite)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
                    .cornerRadius(10)
                }
                Spacer()
            }
            .padding(24)
        }
        .background(AppColors.bgGrey)
        .task { balance = await store.balance(chain: chain, address: address) }
    }

    private func short(_ address: String) -> String {
        address.count > 12 ? "\(address.prefix(6))…\(address.suffix(6))" : address
    }
}

/// Wallet password management — reachable from Wallet Details. Shows the
/// current recovery state, an unlock form if locked (isPasswordEncrypted &&
/// not yet unlocked this session), a set/change-password form otherwise.
/// Backs onto waas.getWalletRecoveryState/unlockWallet/
/// setWaasWalletAccountPassword/updateWaasPassword.
struct WalletPasswordView: View {
    @EnvironmentObject var store: Store
    let wallet: Wallet
    @State private var recoveryState: WalletRecoveryState?
    @State private var loading = true
    @State private var unlockPassword = ""
    @State private var newPassword = ""
    @State private var existingPassword = ""
    @State private var busy = false
    @State private var statusMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .walletDetails(wallet: wallet) }.buttonStyle(AppTextButtonStyle())
                Text("Wallet Password").appSectionHeading()
            }
            if loading {
                loadingCard
            } else if let recoveryState {
                stateCard(recoveryState)
                if recoveryState.isPasswordEncrypted {
                    unlockSection
                }
                if recoveryState.isPasswordEncrypted {
                    changePasswordSection
                } else {
                    setPasswordSection
                }
            } else {
                errorCard
            }
            if let statusMessage {
                Text(statusMessage).appBodySmall(color: AppColors.green)
            }
            Spacer()
        }
        .padding(24)
        .task { await load() }
    }

    private func load() async {
        loading = true
        recoveryState = await store.walletRecoveryState(wallet: wallet)
        loading = false
    }

    private var loadingCard: some View {
        HStack {
            Spacer()
            ProgressView()
            Spacer()
        }
        .padding(12)
        .background(AppColors.bgWhite)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
        .cornerRadius(10)
    }

    private var errorCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Couldn't load wallet recovery state.").appBodyMedium(color: AppColors.txtError)
            Button("Retry") { Task { await load() } }.buttonStyle(AppOutlinedButtonStyle())
        }
        .padding(12)
        .background(AppColors.bgWhite)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
        .cornerRadius(10)
    }

    private func stateCard(_ state: WalletRecoveryState) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Ready state: \(state.walletReadyState)").appBodyMedium()
            Text(state.isPasswordEncrypted ? "Password-encrypted at rest" : "No password set")
                .appBodySmall()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(AppColors.bgWhite)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
        .cornerRadius(10)
    }

    private var unlockSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Unlock wallet").appListItemTitle()
            SecureField("Password", text: $unlockPassword).textFieldStyle(.roundedBorder)
            Button(busy ? "Unlocking…" : "Unlock") {
                Task {
                    busy = true
                    if await store.unlockWallet(wallet: wallet, password: unlockPassword) {
                        statusMessage = "Wallet unlocked for this session."
                        unlockPassword = ""
                    }
                    busy = false
                }
            }
            .buttonStyle(AppOutlinedButtonStyle())
            .disabled(busy || unlockPassword.isEmpty)
        }
    }

    private var setPasswordSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Set a password").appListItemTitle()
            SecureField("New password", text: $newPassword).textFieldStyle(.roundedBorder)
            Button(busy ? "Saving…" : "Set password") {
                Task {
                    busy = true
                    if await store.setWalletPassword(wallet: wallet, newPassword: newPassword) {
                        statusMessage = "Password set."
                        newPassword = ""
                        await load()
                    }
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy || newPassword.isEmpty)
        }
    }

    private var changePasswordSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Change password").appListItemTitle()
            SecureField("Existing password", text: $existingPassword).textFieldStyle(.roundedBorder)
            SecureField("New password", text: $newPassword).textFieldStyle(.roundedBorder)
            Button(busy ? "Saving…" : "Update password") {
                Task {
                    busy = true
                    if await store.updateWalletPassword(wallet: wallet, existingPassword: existingPassword, newPassword: newPassword) {
                        statusMessage = "Password updated."
                        existingPassword = ""
                        newPassword = ""
                    }
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy || existingPassword.isEmpty || newPassword.isEmpty)
        }
    }
}
