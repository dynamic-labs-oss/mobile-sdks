// Home/wallet dashboard. Wallet CREATION is no longer inline here (see
// CreateWallet.kt) — this screen only lists wallets and navigates out to the
// dedicated Create Wallet / Import Private Key screens, matching the
// reference's structure (create_wallet_screen.dart is its own route).
package com.dynamic.demo.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.SdkUser
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.launch

// Chains a wallet card offers a Send action for — every chain with a
// send/transfer primitive somewhere in the SDK (BtcExtension.sendBitcoin and
// SuiExtension.sendTransaction now exist alongside Evm/Solana's).
private val SENDABLE_CHAINS = setOf("EVM", "SOL", "BTC", "SUI")

// Chains with a balance-reading chain extension installed by Sdk.kt.
private val BALANCE_CHAINS = setOf("EVM", "SOL", "BTC", "SUI")

@Composable
fun HomeView(
    user: SdkUser,
    snackbar: SnackbarHostState,
    onUserChanged: (SdkUser) -> Unit,
    onDetails: (wallet: Wallet) -> Unit,
    onSign: (wallet: Wallet) -> Unit,
    onSend: (wallet: Wallet) -> Unit,
    onProfile: () -> Unit,
    onCreateWallet: () -> Unit,
    onImportPrivateKey: () -> Unit,
    onSignOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The generated Wallet model, not a hand-rolled row type — each entry
    // already carries signMessage()/exportPrivateKey() bound to this client.
    val wallets = Sdk.client.wallets.userWallets

    // Headless selection state, reflected from the SDK's typed change events.
    // EVM/Solana/Sui network switching lives in WalletDetails now, scoped to
    // the wallet you tapped into — not here, floating for the whole app.
    var primaryWalletId by remember { mutableStateOf<String?>(null) }

    // Drives the "Setting up your wallets…" banner below. createMissingWaasWallets()
    // is opt-in (matching dynamic-js-sdk's own createWaasWalletAccounts +
    // shouldAutoCreateWalletForChain — the SDK never auto-creates wallets on
    // sign-in by itself), so this app calls it explicitly below and tracks
    // its own loading state; there's no SDK-provided signal for it.
    var syncInProgress by remember { mutableStateOf(false) }
    // Chains the automatic sync failed to create a wallet for, most recent
    // last. One event per failed chain (not per sync pass), so a chain that
    // keeps failing across retries would re-add itself — dismiss removes it
    // client-side only, matching a toast/banner's usual semantics.
    val failedChains = remember { mutableStateListOf<String>() }

    // The SDK's reactive streams (Kotlin Flows). userChanged (hot StateFlow)
    // keeps the list in sync; walletCreated (SharedFlow) pops a snackbar;
    // walletCreationFailed feeds the retry banner below; primaryWalletIdChanged
    // mirrors the headless primary-wallet selection. Collectors live for the
    // composition, each on the Main dispatcher, so state writes are safe.
    LaunchedEffect(Unit) {
        launch { Sdk.client.userChanged.collect { changed -> if (changed != null) onUserChanged(changed) } }
        launch { Sdk.client.walletCreated.collect { wallet -> snackbar.showSnackbar("Wallet created: ${wallet.accountAddress}") } }
        launch { Sdk.client.walletCreationFailed.collect { chain -> if (chain !in failedChains) failedChains.add(chain) } }
        launch { Sdk.client.primaryWalletIdChanged.collect { id -> primaryWalletId = id } }
        // Opt-in post-sign-in wallet sync — this app's own choice to make on
        // landing here, not something the SDK does for it.
        syncInProgress = true
        try {
            Sdk.waas.createMissingWaasWallets()
        } finally {
            syncInProgress = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(user.email ?: "Signed in", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onProfile) { Text("Profile") }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
        Spacer(Modifier.height(12.dp))
        if (syncInProgress) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Setting up your wallets…", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
        failedChains.forEach { failedChain ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Couldn't create your $failedChain wallet.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        // createWallet(chain) IS public — retrying a single
                        // failed chain never needed the (internal-only)
                        // auto-sync method itself, just this.
                        scope.launch {
                            try {
                                Sdk.waas.createWallet(failedChain)
                                failedChains.remove(failedChain)
                            } catch (e: DynamicException) {
                                snackbar.showSnackbar(e.message ?: "Retry failed")
                            }
                        }
                    }) { Text("Retry") }
                    TextButton(onClick = { failedChains.remove(failedChain) }) { Text("Dismiss") }
                }
            }
        }
        Text("Wallets", style = AppTextStyles.SectionHeading)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            AppButton(onClick = onCreateWallet, modifier = Modifier.weight(1f)) {
                Text("+ Create wallet", style = AppTextStyles.ButtonLabel)
            }
            Spacer(Modifier.width(8.dp))
            AppButton(onClick = onImportPrivateKey, modifier = Modifier.weight(1f)) {
                Text("Import key", style = AppTextStyles.ButtonLabel)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (wallets.isEmpty()) {
            Text("No embedded wallets yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            wallets.forEach { wallet ->
                val isPrimary = wallet.id == primaryWalletId
                // Tap the card itself to open Details — no separate "Details"
                // button, matching examples/flutter-app's WalletCard.onPressed
                // (the reference this demo is styled after).
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onDetails(wallet) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (isPrimary) "★ Wallet: ${shortAddr(wallet.address)} (primary)"
                            else "Wallet: ${shortAddr(wallet.address)}",
                            style = if (isPrimary) AppTextStyles.ListItemTitle else MaterialTheme.typography.bodyMedium,
                        )
                        Text("Chain: ${wallet.chain}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            // Not hardcoded any more: userWallets now lists
                            // EXTERNAL wallets too, and each Wallet says which
                            // extension signs for it.
                            if (wallet.isEmbedded) "Type: Embedded (WaaS)"
                            else "Type: External wallet",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (wallet.chain in BALANCE_CHAINS) {
                            WalletBalanceLine(wallet)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!isPrimary) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        try {
                                            // Headless primary-wallet selection (server-recorded).
                                            Sdk.client.wallets.setPrimaryWallet(wallet.id)
                                        } catch (e: Exception) {
                                            snackbar.showSnackbar("Select failed: ${e.message}")
                                        }
                                    }
                                }) { Text("Set primary") }
                            }
                            OutlinedButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("address", wallet.address))
                                scope.launch { snackbar.showSnackbar("Copied to clipboard") }
                            }) { Text("Copy") }
                            OutlinedButton(onClick = { onSign(wallet) }) { Text("Sign") }
                            if (wallet.chain in SENDABLE_CHAINS) {
                                OutlinedButton(onClick = { onSend(wallet) }) { Text("Send") }
                            }
                        }
                    }
                }
            }
        }
    }
}
