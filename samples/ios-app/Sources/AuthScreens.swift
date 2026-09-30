import DynamicSDK
import SwiftUI

// Step-up (MFA) gate, mirroring the Flutter/Kotlin/MAUI demos: generic over
// whatever credentials the server reports for the scope — prefers an
// already-registered TOTP device, falls back to email-OTP reauth when the
// user has none (same has-MFA vs. no-MFA branch as dynamic-auth's
// usePromptStepUpAuth). Other credential kinds aren't wired up yet.
private enum StepUpMethod { case totp, email }

struct StepUpView: View {
    @EnvironmentObject var store: Store
    let request: StepUpRequest
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?
    @State private var emailVerificationUuid: String?

    private func isTotp(_ c: StepUpCredential) -> Bool { c.type == "totp" }
    private func isEmail(_ c: StepUpCredential) -> Bool { c.format == "email" }

    private var method: StepUpMethod? {
        if request.credentials.contains(where: isTotp) { return .totp }
        if request.credentials.contains(where: isEmail) { return .email }
        return nil
    }

    private func label(_ c: StepUpCredential) -> String {
        if let alias = c.alias { return alias }
        switch c.type {
        case "totp": return "Authenticator app (TOTP)"
        case "passkey": return "Passkey"
        default: return c.type ?? c.format ?? "Credential"
        }
    }

    private func sendEmailOtp() async {
        busy = true
        defer { busy = false }
        do {
            let sent = try await store.sendStepUpEmailOtp()
            emailVerificationUuid = sent.verificationUuid
        } catch {
            self.error = "\(error)"
        }
    }

    private func verify() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            switch method {
            case .totp:
                try await store.completeTotpStepUp(code: code.trimmingCharacters(in: .whitespaces), scope: request.scope)
            case .email:
                guard let uuid = emailVerificationUuid else { return }
                try await store.completeEmailOtpStepUp(
                    verificationUuid: uuid, code: code.trimmingCharacters(in: .whitespaces), scope: request.scope
                )
            case nil:
                return
            }
            store.completeStepUp(true)
        } catch {
            self.error = "\(error)"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Verify it's you").appSectionHeading()
            Text("This action needs step-up authentication.").appBodySmall()
            ForEach(request.credentials, id: \.id) { credential in
                let supported = isTotp(credential) || isEmail(credential)
                HStack {
                    Image(systemName: supported ? "checkmark.circle" : "lock")
                    Text(supported ? label(credential) : "\(label(credential)) — not in this demo")
                        .appBodyMedium(color: supported ? AppColors.txtPrimary : AppColors.txtSecondary)
                }
            }
            if method == .email {
                Text(emailVerificationUuid != nil ? "Code sent to your email." : "Sending a code to your email…")
                    .appBodySmall()
            }
            if let method, method == .totp || emailVerificationUuid != nil {
                TextField(method == .totp ? "Authenticator code" : "Email code", text: $code)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.numberPad)
            } else if method == nil {
                Text("No supported step-up method for this demo.")
                    .appBodyMedium(color: AppColors.txtError)
            }
            if let error {
                Text(error).appBodySmall(color: AppColors.txtError)
            }
            if busy { ProgressView() }
            HStack {
                Button("Cancel") { store.completeStepUp(false) }
                    .buttonStyle(AppTextButtonStyle())
                    .disabled(busy)
                Spacer()
                if method != nil {
                    Button("Verify") { Task { await verify() } }
                        .buttonStyle(AppPrimaryButtonStyle())
                        .disabled(busy || (method == .email && emailVerificationUuid == nil))
                }
            }
        }
        .padding(24)
        .task {
            if method == .email { await sendEmailOtp() }
        }
    }
}

/// Step-Up Auth standalone entry point (CAPABILITIES.md) — the modal
/// (StepUpView above) already exists, triggered inline wherever a
/// privileged action needs it; this screen just lets a user proactively
/// trigger the same ensureStepUp(scope:) gate from Home/Profile, without
/// first performing a privileged action.
struct StepUpEntryView: View {
    @EnvironmentObject var store: Store
    @State private var scope = "wallet:sign"
    @State private var busy = false
    @State private var result: String?

    private let scopes = ["wallet:sign", "wallet:export"]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Button("← Back") { store.screen = .profile }.buttonStyle(AppTextButtonStyle())
                Text("Step-Up Auth").appSectionHeading()
            }
            Text("Proactively verify it's you for a scope, before you need it.").appBodySmall()
            Text("Scope").appBodySmall()
            Picker("Scope", selection: $scope) {
                ForEach(scopes, id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(.segmented)
            Button(busy ? "Checking…" : "Start step-up") {
                Task {
                    busy = true
                    let ok = await store.ensureStepUp(scope: scope)
                    result = ok ? "Step-up complete for \(scope)." : "Step-up was not completed."
                    busy = false
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(busy)
            if let result {
                Text(result).appBodySmall(color: AppColors.green)
            }
            Spacer()
        }
        .padding(24)
    }
}

/// Passkeys management — list-item-row pattern (DESIGN.md §7): each passkey
/// shows alias/device info, a delete button (destructive/red); a "Register
/// passkey" CTA at the top; loading/error/empty states per the standard
/// pattern.
struct PasskeysView: View {
    @EnvironmentObject var store: Store
    @State private var passkeys: [UserPasskey]?
    @State private var loading = true
    @State private var registering = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Button("← Back") { store.screen = .profile }.buttonStyle(AppTextButtonStyle())
                    Text("Passkeys").appSectionHeading()
                }
                Button(registering ? "Registering…" : "Register passkey") {
                    Task {
                        registering = true
                        if await store.registerPasskey() { await load() }
                        registering = false
                    }
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .disabled(registering)
                content
            }
            .padding(16)
        }
        .background(AppColors.bgGrey)
        .task { await load() }
    }

    private func load() async {
        loading = true
        passkeys = await store.getPasskeys()
        loading = false
    }

    @ViewBuilder
    private var content: some View {
        if loading {
            card { HStack { Spacer(); ProgressView(); Spacer() } }
        } else if let passkeys {
            if passkeys.isEmpty {
                card {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("No passkeys configured").appListItemTitle()
                        Text("Register one above to sign in without a password.").appBodySmall()
                    }
                }
            } else {
                ForEach(passkeys, id: \.id) { passkey in
                    passkeyRow(passkey)
                }
            }
        } else {
            card {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Couldn't load passkeys.").appBodyMedium(color: AppColors.txtError)
                    Button("Retry") { Task { await load() } }.buttonStyle(AppOutlinedButtonStyle())
                }
            }
        }
    }

    private func passkeyRow(_ passkey: UserPasskey) -> some View {
        card {
            VStack(alignment: .leading, spacing: 12) {
                Text(passkey.alias ?? "Passkey").appListItemTitle()
                Text("id: \(passkey.id)").appBodySmall(color: AppColors.grey600)
                Text("created: \(passkey.createdAt)").appBodySmall(color: AppColors.grey600)
                if let storage = passkey.storage {
                    Text(storage.name).appBodySmall(color: AppColors.grey600)
                }
                Button("Delete") {
                    Task {
                        if await store.deletePasskey(passkeyId: passkey.id) { await load() }
                    }
                }
                .buttonStyle(AppOutlinedButtonStyle(labelColor: AppColors.red))
                .frame(maxWidth: .infinity)
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

/// MFA recovery codes — shows current codes (monospace, grey-200 chip), a
/// "Generate new codes" button, and an acknowledgment step gated behind
/// isPendingMfaRecoveryCodesAcknowledgment before the codes are considered
/// saved.
struct MfaRecoveryCodesView: View {
    @EnvironmentObject var store: Store
    @State private var codes: MfaRecoveryCodesResponse?
    @State private var loading = true
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    // Gated behind isPendingMfaRecoveryCodesAcknowledgment
                    // (CAPABILITIES.md: "before the codes screen is
                    // dismissible") — re-enables on its own once
                    // acknowledgeMfaRecoveryCodes refreshes state.user.
                    Button("← Back") { store.screen = .profile }
                        .buttonStyle(AppTextButtonStyle())
                        .disabled(store.isPendingMfaRecoveryCodesAcknowledgment)
                    Text("MFA Recovery Codes").appSectionHeading()
                }
                if store.isPendingMfaRecoveryCodesAcknowledgment {
                    acknowledgmentBanner
                }
                content
                Button(busy ? "Generating…" : "Generate new codes") {
                    Task {
                        busy = true
                        if let generated = await store.createNewMfaRecoveryCodes() { codes = generated }
                        busy = false
                    }
                }
                .buttonStyle(AppOutlinedButtonStyle())
                .disabled(busy)
            }
            .padding(16)
        }
        .background(AppColors.bgGrey)
        .task { await load() }
    }

    private func load() async {
        loading = true
        codes = await store.getMfaRecoveryCodes()
        loading = false
    }

    private var acknowledgmentBanner: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Save these codes somewhere safe before leaving this screen.")
                .appBodyMedium(color: AppColors.orange)
            Button("I've saved my codes") {
                Task { _ = await store.acknowledgeMfaRecoveryCodes() }
            }
            .buttonStyle(AppOutlinedButtonStyle())
        }
        .padding(12)
        .background(AppColors.orange.opacity(0.1))
        .cornerRadius(8)
    }

    @ViewBuilder
    private var content: some View {
        if loading {
            card { HStack { Spacer(); ProgressView(); Spacer() } }
        } else if let codes {
            if codes.recoveryCodes.isEmpty {
                card {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("No recovery codes configured").appListItemTitle()
                        Text("Generate a set below.").appBodySmall()
                    }
                }
            } else {
                card {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("\(codes.count) codes").appListItemTitle()
                        ForEach(codes.recoveryCodes, id: \.self) { code in
                            Text(code)
                                .font(.system(.body, design: .monospaced))
                                .textSelection(.enabled)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(AppColors.grey200)
                                .cornerRadius(4)
                        }
                    }
                }
            }
        } else {
            card {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Couldn't load recovery codes.").appBodyMedium(color: AppColors.txtError)
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
