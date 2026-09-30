// Business account list and detail screens.
package com.dynamic.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.demo.StepUpHost
import com.dynamic.demo.rememberStepUpGate
import com.dynamic.sdk.BusinessAccount
import com.dynamic.sdk.BusinessAccountDetail
import com.dynamic.sdk.BusinessAccountResult
import com.dynamic.sdk.BusinessAccountTargetIdentity
import com.dynamic.sdk.DynamicException
import kotlinx.coroutines.launch

@Composable
fun BusinessAccountsListView(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<BusinessAccount>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var newExternalRef by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            accounts = Sdk.businessAccount.listBusinessAccounts().items ?: emptyList()
        } catch (e: DynamicException) {
            error = e.message ?: "Failed to load business accounts"
        } catch (e: Exception) {
            error = "native: ${e.message}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Business Accounts", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text("Create business account", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Name (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newExternalRef,
            onValueChange = { newExternalRef = it },
            label = { Text("External reference (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !creating,
            onClick = {
                creating = true
                scope.launch {
                    try {
                        val created = Sdk.businessAccount.createBusinessAccount(
                            newName.trim().ifBlank { null },
                            newExternalRef.trim().ifBlank { null },
                        )
                        newName = ""
                        newExternalRef = ""
                        reload()
                        onOpen(created.id)
                    } catch (e: DynamicException) {
                        snackbar.showSnackbar(e.message ?: "Create failed")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("native: ${e.message}")
                    } finally {
                        creating = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (creating) "Creating…" else "Create business account", style = AppTextStyles.ButtonLabel) }

        Spacer(Modifier.height(24.dp))
        when {
            loading -> Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TxtError))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { scope.launch { reload() } }) { Text("Retry") }
                }
            }
            accounts.isNullOrEmpty() -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No business accounts configured", style = AppTextStyles.ListItemTitle)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create one above to manage multi-operator wallets.",
                        style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
                    )
                }
            }
            else -> accounts!!.forEach { account ->
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onOpen(account.id) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(account.name ?: account.id, style = AppTextStyles.ListItemTitle)
                        Text(
                            "id: ${account.id.take(8)}…",
                            style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600),
                        )
                    }
                }
            }
        }
    }
}

private val MEMBER_ROLES = listOf("admin", "viewer")

@Composable
fun BusinessAccountDetailView(
    businessAccountId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    // transferBusinessAccountOwnership's docstring: "The server revokes the
    // caller's session as a side effect, so this also tears down the local
    // session exactly like signOut." A post-transfer reload() would just
    // fail unauthenticated — this screen has to exit to Login instead.
    onSessionEnded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stepUp = rememberStepUpGate()
    var detail by remember { mutableStateOf<BusinessAccountDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var walletIdToLink by remember { mutableStateOf("") }
    var memberUserId by remember { mutableStateOf("") }
    var memberIdentifier by remember { mutableStateOf("") }
    var memberRole by remember { mutableStateOf(MEMBER_ROLES.first()) }
    var memberRoleMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            detail = Sdk.businessAccount.getBusinessAccount(businessAccountId)
            renameText = detail?.name ?: ""
        } catch (e: DynamicException) {
            error = e.message ?: "Failed to load business account"
        } catch (e: Exception) {
            error = "native: ${e.message}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(businessAccountId) { reload() }

    // Every mutating BusinessAccountExtension method's docstring names an
    // elevated scope (business_account:member:add, :member:remove, etc.) —
    // [elevatedScope] null means the call is genuinely unelevated
    // (updateBusinessAccount's docstring says so explicitly).
    fun runAction(elevatedScope: String?, action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                if (elevatedScope != null && !stepUp.ensure(elevatedScope)) return@launch
                action()
                reload()
            } catch (e: DynamicException) {
                snackbar.showSnackbar(e.message ?: "Action failed")
            } catch (e: Exception) {
                snackbar.showSnackbar("native: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    // An applied ownership transfer ends the current session.
    fun transferOwnership(newOwnerUserId: String) {
        busy = true
        scope.launch {
            try {
                if (!stepUp.ensure("business_account:transfer_ownership")) return@launch
                val result = Sdk.businessAccount.transferBusinessAccountOwnership(businessAccountId, newOwnerUserId)
                if (result is BusinessAccountResult.ActionRequired) {
                    snackbar.showSnackbar("Approval required")
                    busy = false
                    return@launch
                }
                onSessionEnded()
            } catch (e: DynamicException) {
                snackbar.showSnackbar(e.message ?: "Transfer failed")
                busy = false
            } catch (e: Exception) {
                snackbar.showSnackbar("native: ${e.message}")
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Business Account", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        if (loading) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            return@Column
        }
        if (error != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TxtError))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { scope.launch { reload() } }) { Text("Retry") }
                }
            }
            return@Column
        }
        val current = detail ?: return@Column

        Text(current.name ?: current.id, style = AppTextStyles.ListItemTitle)
        Text("id: ${current.id}", style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            enabled = !busy,
            // Not elevated-scope — updateBusinessAccount's own docstring:
            // "a plain metadata edit."
            onClick = { runAction(null) { Sdk.businessAccount.updateBusinessAccount(businessAccountId, renameText.trim()) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rename account") }

        Spacer(Modifier.height(24.dp))
        Text("Members", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        current.members.forEach { member ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(member.userId, style = MaterialTheme.typography.bodyMedium)
                        Text(member.role, style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600))
                    }
                    if (member.role != "owner") {
                        val nextRole = if (member.role == "admin") "viewer" else "admin"
                        TextButton(
                            onClick = {
                                runAction("business_account:member:role:update") {
                                    Sdk.businessAccount.updateBusinessAccountMemberRole(businessAccountId, member.userId, nextRole)
                                }
                            },
                        ) { Text("Make $nextRole") }
                        TextButton(onClick = { transferOwnership(member.userId) }) { Text("Make owner") }
                        TextButton(
                            onClick = {
                                runAction("business_account:member:remove") {
                                    Sdk.businessAccount.removeBusinessAccountMember(businessAccountId, member.userId)
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Red),
                        ) { Text("Remove") }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = memberUserId,
            onValueChange = { memberUserId = it },
            label = { Text("User ID (or leave blank + use email below)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = memberIdentifier,
            onValueChange = { memberIdentifier = it },
            label = { Text("Email (used when User ID is blank)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { memberRoleMenu = true }) { Text(memberRole) }
            DropdownMenu(expanded = memberRoleMenu, onDismissRequest = { memberRoleMenu = false }) {
                MEMBER_ROLES.forEach { role ->
                    DropdownMenuItem(text = { Text(role) }, onClick = { memberRole = role; memberRoleMenu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                runAction("business_account:member:add") {
                    if (memberUserId.isNotBlank()) {
                        Sdk.businessAccount.addBusinessAccountMember(
                            businessAccountId,
                            BusinessAccountTargetIdentity(userId = memberUserId.trim()),
                            memberRole,
                        )
                    } else {
                        Sdk.businessAccount.addBusinessAccountMember(
                            businessAccountId,
                            BusinessAccountTargetIdentity(
                                identifier = memberIdentifier.trim(),
                                identifierType = "email",
                            ),
                            memberRole,
                        )
                    }
                    memberUserId = ""
                    memberIdentifier = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add member", style = AppTextStyles.ButtonLabel) }

        Spacer(Modifier.height(24.dp))
        Text("Wallets", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        current.wallets.orEmpty().forEach { wallet ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(wallet.chain, style = MaterialTheme.typography.bodyMedium)
                        Text(shortAddr(wallet.publicKey), style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600))
                    }
                    TextButton(
                        onClick = {
                            runAction("business_account:wallet:remove") {
                                Sdk.businessAccount.removeBusinessAccountWallet(businessAccountId, wallet.id)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Red),
                    ) { Text("Remove") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = walletIdToLink,
            onValueChange = { walletIdToLink = it },
            label = { Text("Wallet ID") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                runAction("business_account:link_wallet") {
                    Sdk.businessAccount.addWalletToBusinessAccount(walletIdToLink.trim(), businessAccountId)
                    walletIdToLink = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Link wallet", style = AppTextStyles.ButtonLabel) }

        Spacer(Modifier.height(24.dp))
        Text("Signers", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        if (current.signers.isEmpty()) {
            Text(
                "No signers configured",
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
            )
        } else {
            current.signers.forEach { signer ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(signer.userId ?: signer.id, style = MaterialTheme.typography.bodyMedium)
                            Text("${signer.type} · ${signer.walletId}", style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600))
                        }
                        TextButton(
                            onClick = {
                                runAction("business_account:signer:remove") {
                                    Sdk.businessAccount.removeBusinessAccountSigner(businessAccountId, signer.walletId, signer.id)
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Red),
                        ) { Text("Remove") }
                    }
                }
            }
        }
    }
    StepUpHost(stepUp)
}
