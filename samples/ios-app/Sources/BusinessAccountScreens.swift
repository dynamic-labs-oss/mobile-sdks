import DynamicSDK
import DynamicSdkBusinessAccount
import SwiftUI

/// Business Accounts — list screen (listBusinessAccounts, list-item-row
/// pattern, "Create business account" CTA). Tapping a row opens
/// BusinessAccountDetailView.
struct BusinessAccountsListView: View {
    @EnvironmentObject var store: Store
    @State private var accounts: [BusinessAccount]?
    @State private var loading = true
    @State private var newName = ""
    @State private var creating = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button("← Back") { store.screen = .profile }.buttonStyle(AppTextButtonStyle())
                    Text("Business Accounts").appSectionHeading()
                }
                HStack {
                    TextField("New business account name", text: $newName).textFieldStyle(.roundedBorder)
                    Button(creating ? "Creating…" : "Create") {
                        Task {
                            creating = true
                            if await store.createBusinessAccount(name: newName) {
                                newName = ""
                                await load()
                            }
                            creating = false
                        }
                    }
                    .buttonStyle(AppOutlinedButtonStyle())
                    .disabled(creating || newName.isEmpty)
                }
                content
            }
            .padding(16)
        }
        .background(AppColors.bgGrey)
        .task { await load() }
    }

    private func load() async {
        loading = true
        accounts = await store.listBusinessAccounts()
        loading = false
    }

    @ViewBuilder
    private var content: some View {
        if loading {
            card { HStack { Spacer(); ProgressView(); Spacer() } }
        } else if let accounts {
            if accounts.isEmpty {
                card {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("No business accounts configured").appListItemTitle()
                        Text("Create one above.").appBodySmall()
                    }
                }
            } else {
                ForEach(accounts, id: \.id) { account in
                    card {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(account.name ?? account.id).appListItemTitle()
                            Text("id: \(account.id)").appBodySmall(color: AppColors.grey600)
                            if let createdAt = account.createdAt {
                                Text("created: \(createdAt)").appBodySmall(color: AppColors.grey600)
                            }
                            Button("View details") { store.screen = .businessAccountDetail(account: account) }
                                .buttonStyle(AppOutlinedButtonStyle())
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        } else {
            card {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Couldn't load business accounts.").appBodyMedium(color: AppColors.txtError)
                    Button("Retry") { Task { await load() } }.buttonStyle(AppOutlinedButtonStyle())
                }
            }
        }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(AppColors.bgWhite)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
            .cornerRadius(10)
    }
}

/// Business account detail — members list (role/remove/transfer-ownership
/// actions), linked wallets list (link/remove actions), signers list
/// (remove action). Kept as one card section each per CAPABILITIES.md's
/// "fine to keep... simple" note, rather than separate sub-screens.
struct BusinessAccountDetailView: View {
    @EnvironmentObject var store: Store
    let account: BusinessAccount
    @State private var detail: BusinessAccountDetail?
    @State private var loading = true

    @State private var memberIdentifier = ""
    @State private var memberIdentifierType = "email"
    @State private var memberRole = "admin"
    @State private var newOwnerUserId = ""
    @State private var walletIdToLink = ""
    @State private var busy = false

    private let identifierTypes = ["email", "id", "externalUserId"]
    private let roles = ["admin", "viewer"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button("← Back") { store.screen = .businessAccounts }.buttonStyle(AppTextButtonStyle())
                    Text(account.name ?? "Business Account").appSectionHeading()
                }
                if loading {
                    card { HStack { Spacer(); ProgressView(); Spacer() } }
                } else if let detail {
                    membersSection(detail)
                    linkedWalletsSection(detail)
                    signersSection(detail)
                } else {
                    card {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Couldn't load business account.").appBodyMedium(color: AppColors.txtError)
                            Button("Retry") { Task { await load() } }.buttonStyle(AppOutlinedButtonStyle())
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(AppColors.bgGrey)
        .disabled(busy)
        .task { await load() }
    }

    private func load() async {
        loading = true
        detail = await store.getBusinessAccount(id: account.id)
        loading = false
    }

    private func membersSection(_ detail: BusinessAccountDetail) -> some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text("Members").appListItemTitle()
                ForEach(detail.members, id: \.id) { member in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(member.userId) — \(member.role)").appBodyMedium()
                        HStack(spacing: Spacing.space8) {
                            if member.role != "owner" {
                                Button("Remove") {
                                    Task {
                                        busy = true
                                        if await store.removeBusinessAccountMember(businessAccountId: account.id, userId: member.userId) { await load() }
                                        busy = false
                                    }
                                }
                                .buttonStyle(AppOutlinedButtonStyle(labelColor: AppColors.red))
                            }
                        }
                    }
                }
                Divider()
                Text("Add member").appBodySmall()
                TextField("Identifier (email/id)", text: $memberIdentifier).textFieldStyle(.roundedBorder)
                Picker("Identifier type", selection: $memberIdentifierType) {
                    ForEach(identifierTypes, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.segmented)
                Picker("Role", selection: $memberRole) {
                    ForEach(roles, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.segmented)
                Button("Add member") {
                    Task {
                        busy = true
                        if await store.addBusinessAccountMember(
                            businessAccountId: account.id, role: memberRole, identifier: memberIdentifier, identifierType: memberIdentifierType
                        ) {
                            memberIdentifier = ""
                            await load()
                        }
                        busy = false
                    }
                }
                .buttonStyle(AppOutlinedButtonStyle())
                .disabled(memberIdentifier.isEmpty)
                Divider()
                Text("Transfer ownership").appBodySmall()
                TextField("New owner user id", text: $newOwnerUserId).textFieldStyle(.roundedBorder)
                Button("Transfer ownership") {
                    Task {
                        busy = true
                        // On success, Store already tears down the local
                        // session (the server revokes it as a side effect —
                        // see transferBusinessAccountOwnership's doc) and
                        // navigates to .login — no re-fetch here, it would
                        // just 401 against the now-revoked token.
                        _ = await store.transferBusinessAccountOwnership(businessAccountId: account.id, newOwnerUserId: newOwnerUserId)
                        busy = false
                    }
                }
                .buttonStyle(AppOutlinedButtonStyle(labelColor: AppColors.red))
                .disabled(newOwnerUserId.isEmpty)
            }
        }
    }

    private func linkedWalletsSection(_ detail: BusinessAccountDetail) -> some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text("Linked wallets").appListItemTitle()
                ForEach(detail.wallets ?? [], id: \.id) { wallet in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(wallet.chain): \(wallet.publicKey)").appBodyMedium()
                        Button("Unlink") {
                            Task {
                                busy = true
                                if await store.removeBusinessAccountWallet(businessAccountId: account.id, walletId: wallet.id) { await load() }
                                busy = false
                            }
                        }
                        .buttonStyle(AppOutlinedButtonStyle(labelColor: AppColors.red))
                    }
                }
                Divider()
                Text("Link a wallet").appBodySmall()
                Picker("Wallet", selection: $walletIdToLink) {
                    Text("Select a wallet").tag("")
                    ForEach(store.wallets, id: \.id) { wallet in
                        Text("\(wallet.chain) \(wallet.address.prefix(10))…").tag(wallet.id)
                    }
                }
                .pickerStyle(.menu)
                Button("Link wallet") {
                    Task {
                        busy = true
                        if await store.linkBusinessAccountWallet(walletId: walletIdToLink, businessAccountId: account.id) {
                            walletIdToLink = ""
                            await load()
                        }
                        busy = false
                    }
                }
                .buttonStyle(AppOutlinedButtonStyle())
                .disabled(walletIdToLink.isEmpty)
            }
        }
    }

    private func signersSection(_ detail: BusinessAccountDetail) -> some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text("Signers").appListItemTitle()
                if detail.signers.isEmpty {
                    Text("No signers configured").appBodySmall()
                }
                ForEach(detail.signers, id: \.id) { signer in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(signer.type) — wallet \(signer.walletId)").appBodyMedium()
                        Button("Remove") {
                            Task {
                                busy = true
                                if await store.removeBusinessAccountSigner(businessAccountId: account.id, walletId: signer.walletId, signerId: signer.id) {
                                    await load()
                                }
                                busy = false
                            }
                        }
                        .buttonStyle(AppOutlinedButtonStyle(labelColor: AppColors.red))
                    }
                }
            }
        }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(AppColors.bgWhite)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
            .cornerRadius(10)
    }
}
