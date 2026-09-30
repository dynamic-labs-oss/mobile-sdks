package com.dynamic.demo.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.launch

@Composable
fun WalletDetailsView(
    wallet: Wallet,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSign: () -> Unit,
    onSignTypedData: () -> Unit,
    onSend: () -> Unit,
    onExportKey: () -> Unit,
    onWalletPassword: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var networkMenu by remember { mutableStateOf(false) }

    // EVM/Solana/Sui network switcher — moved here from Home, scoped to this
    // wallet's own chain family. NOTE the underlying selection is still
    // client-wide (setActiveEvmChainId/setActiveSolanaNetwork/setActiveSuiNetwork
    // have exactly one value each, shared by every wallet of that chain
    // family — see wallets.flows.ts/state.ts), so this fixes PLACEMENT, not
    // independence; flagged via the caption under the picker rather than
    // implied away.
    val networks = remember(wallet.chain) {
        if (wallet.chain == "EVM") Sdk.evm.availableNetworks() else emptyList()
    }
    val solanaNetworks = remember(wallet.chain) {
        if (wallet.chain == "SOL") Sdk.solana.availableNetworks() else emptyList()
    }
    val suiNetworks = remember(wallet.chain) {
        if (wallet.chain == "SUI") Sdk.sui.availableNetworks() else emptyList()
    }
    var activeChainId by remember { mutableStateOf(Sdk.client.wallets.activeEvmChainId ?: networks.firstOrNull()?.chainId) }
    var activeSolanaNetworkName by remember {
        mutableStateOf(Sdk.client.wallets.activeSolanaNetwork ?: solanaNetworks.firstOrNull()?.name)
    }
    var activeSuiNetworkName by remember {
        mutableStateOf(Sdk.client.wallets.activeSuiNetwork ?: suiNetworks.firstOrNull()?.name)
    }
    val activeNetworkName = networks.firstOrNull { it.chainId == activeChainId }?.name ?: "$activeChainId"

    LaunchedEffect(Unit) {
        launch { Sdk.client.activeEvmChainIdChanged.collect { id -> if (id != null) activeChainId = id } }
        launch { Sdk.client.activeSolanaNetworkNameChanged.collect { name -> if (name != null) activeSolanaNetworkName = name } }
        launch { Sdk.client.activeSuiNetworkNameChanged.collect { name -> if (name != null) activeSuiNetworkName = name } }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Wallet Details", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Chain: ${wallet.chain}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (wallet.isEmbedded) "Type: Embedded (WaaS)"
                    else "Type: External wallet",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Address:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(wallet.address, style = MaterialTheme.typography.bodyMedium)
        // BTC has a balance (read-only chain extension) but no network
        // picker below (mainnet/testnet aren't the same derivation, unlike
        // EVM/Solana/Sui's interchangeable networks) — see BtcExtension's doc.
        if (wallet.chain == "EVM" || wallet.chain == "SOL" || wallet.chain == "BTC" || wallet.chain == "SUI") {
            Spacer(Modifier.height(8.dp))
            WalletBalanceLine(wallet)
        }
        if (wallet.chain == "EVM") {
            Spacer(Modifier.height(24.dp))
            Text("Network:", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { networkMenu = true }) { Text(activeNetworkName) }
                DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                    networks.forEach { network ->
                        DropdownMenuItem(
                            text = { Text(network.name) },
                            onClick = {
                                networkMenu = false
                                scope.launch { Sdk.client.wallets.setActiveEvmChainId(network.chainId) }
                            },
                        )
                    }
                }
            }
            Text(
                "Applies to all EVM wallets on this client.",
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
            )
        } else if (wallet.chain == "SOL") {
            Spacer(Modifier.height(24.dp))
            Text("Network:", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { networkMenu = true }) { Text(activeSolanaNetworkName ?: "Solana") }
                DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                    solanaNetworks.forEach { network ->
                        DropdownMenuItem(
                            text = { Text(network.name) },
                            onClick = {
                                networkMenu = false
                                scope.launch { Sdk.client.wallets.setActiveSolanaNetwork(network.name) }
                            },
                        )
                    }
                }
            }
            Text(
                "Applies to all Solana wallets on this client.",
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
            )
        } else if (wallet.chain == "SUI") {
            Spacer(Modifier.height(24.dp))
            Text("Network:", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { networkMenu = true }) { Text(activeSuiNetworkName ?: "Sui") }
                DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                    suiNetworks.forEach { network ->
                        DropdownMenuItem(
                            text = { Text(network.name) },
                            onClick = {
                                networkMenu = false
                                scope.launch { Sdk.client.wallets.setActiveSuiNetwork(network.name) }
                            },
                        )
                    }
                }
            }
            Text(
                "Applies to all Sui wallets on this client.",
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
            )
        }
        if (wallet.chain == "EVM" || wallet.chain == "SOL") {
            Spacer(Modifier.height(16.dp))
            Text("Balances on all networks:", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            // No getMultichainBalance primitive exists in the SDK — this is a
            // demo-app-side loop over availableNetworks(), fetched concurrently
            // and caught per-network so one dead RPC doesn't blank the list.
            MultichainBalances(wallet, networks, solanaNetworks)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("address", wallet.address))
                scope.launch { snackbar.showSnackbar("Copied to clipboard") }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Copy Address") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSign, modifier = Modifier.fillMaxWidth()) { Text("Sign Message") }
        // EVM-only, per CAPABILITIES.md's Sign Typed Data entry.
        if (wallet.chain == "EVM") {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSignTypedData, modifier = Modifier.fillMaxWidth()) { Text("Sign Typed Data") }
        }
        // EVM/Solana/BTC/Sui: exercise the matching chain extension's send —
        // was only reachable from the Home wallet card, not from here.
        if (wallet.chain == "EVM" || wallet.chain == "SOL" || wallet.chain == "BTC" || wallet.chain == "SUI") {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                Text(if (wallet.chain == "BTC") "Send Bitcoin" else "Send")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onWalletPassword, modifier = Modifier.fillMaxWidth()) { Text("Wallet Password") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExportKey, modifier = Modifier.fillMaxWidth()) { Text("Export Private Key") }
    }
}
