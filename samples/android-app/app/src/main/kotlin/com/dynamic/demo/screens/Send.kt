// Send — chain-branches across every sendable chain (EVM/Solana/BTC/Sui).
// Extended from the original EVM/Solana-only screen per CAPABILITIES.md's
// "Sui and BTC actions on Wallet Details" bullet: BtcExtension.sendBitcoin
// and SuiExtension.sendTransaction both exist now, so this screen just needs
// its chain-branching widened, not a new screen.
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.demo.StepUpHost
import com.dynamic.demo.rememberStepUpGate
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.EvmTxRequest
import com.dynamic.sdk.SolanaTxRequest
import com.dynamic.sdk.SuiTxRequest
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dynamic.sdk.btc.sendBitcoin

@Composable
fun SendView(
    wallet: Wallet,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chain = wallet.chain
    val address = wallet.address
    var to by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(if (chain == "EVM") "0.001" else "0.01") }
    var balance by remember { mutableStateOf<String?>(null) }
    var txHash by remember { mutableStateOf<String?>(null) }
    var suiStatus by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var networkMenu by remember { mutableStateOf(false) }
    val stepUp = rememberStepUpGate()
    val unitName = unitNameFor(chain)
    val unitsPerToken = unitsPerTokenFor(chain)
    val toLabel = when (chain) {
        "EVM" -> "To address (0x…)"
        else -> "To address"
    }
    // Reflects the headless active network chosen on Wallet Details.
    val suiNetworks = remember(chain) {
        if (chain == "SUI") Sdk.sui.availableNetworks() else emptyList()
    }
    var activeSuiNetworkName by remember {
        mutableStateOf(Sdk.client.wallets.activeSuiNetwork ?: suiNetworks.firstOrNull()?.name)
    }
    val activeSuiNetwork = suiNetworks.firstOrNull { it.name == activeSuiNetworkName }
    val networkName = when (chain) {
        "SOL" -> Sdk.client.wallets.activeSolanaNetwork ?: Sdk.solana.availableNetworks().first().name
        "SUI" -> activeSuiNetworkName ?: "Sui"
        "BTC" -> Sdk.btc.availableNetworks().first().name
        else -> Sdk.evm.availableNetworks().firstOrNull { it.chainId == Sdk.evm.getChainId() }?.name ?: "EVM"
    }

    // Load the on-chain balance for this wallet on entry.
    LaunchedEffect(address, chain, activeSuiNetworkName) {
        balance = try {
            val units = when (chain) {
                "SOL" -> java.math.BigInteger(Sdk.solana.getBalance(address))
                "SUI" -> java.math.BigInteger(Sdk.sui.getBalance(address))
                "BTC" -> java.math.BigInteger(Sdk.btc.getBalance(address))
                else -> Sdk.evm.getBalance(address)
            }
            "${unitsToToken(units, unitsPerToken)} $unitName"
        } catch (e: Exception) {
            "unavailable (${e.message})"
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Send ($networkName)", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(8.dp))
        Text("From: ${shortAddr(address)}", style = MaterialTheme.typography.bodyMedium)
        Text("Balance: ${balance ?: "loading…"}", style = MaterialTheme.typography.bodyMedium)
        if (chain == "SUI") {
            Spacer(Modifier.height(16.dp))
            Text("Network", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { networkMenu = true }) {
                    Text(activeSuiNetworkName ?: "Select Sui network")
                }
                DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                    suiNetworks.forEach { network ->
                        DropdownMenuItem(
                            text = { Text(network.name) },
                            onClick = {
                                networkMenu = false
                                Sdk.client.wallets.setActiveSuiNetwork(network.name)
                                activeSuiNetworkName = network.name
                                balance = null
                            },
                        )
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Sui transfer test", style = MaterialTheme.typography.bodyLarge)
                    Text("1. Select the Sui network above.")
                    Text("2. Fund this address on the same network.")
                    Text("3. Enter another address on that network and an amount.")
                    Text("4. Send, approve the signature, and check the digest status.")
                    if (activeSuiNetwork?.cluster == "mainnet") {
                        Text("Mainnet uses real SUI. The faucet is not available.")
                    } else {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            val command = "sui client faucet --address $address"
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Sui faucet command", command))
                            scope.launch { snackbar.showSnackbar("Faucet command copied") }
                        }) { Text("Copy faucet command") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            label = { Text(toLabel) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount ($unitName)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(16.dp))
        AppButton(
            onClick = send@{
                if (to.trim().isEmpty()) {
                    scope.launch { snackbar.showSnackbar("Enter a recipient address") }
                    return@send
                }
                busy = true
                txHash = null
                suiStatus = null
                scope.launch {
                    try {
                        // Signing gate: same generic step-up dialog as SignView.
                        if (!stepUp.ensure("wallet:sign")) return@launch
                        val units = tokenToUnits(amount.trim(), unitsPerToken)
                        if (units.signum() <= 0) {
                            throw DynamicException.State("Amount must be greater than zero")
                        }
                        val digest = when (chain) {
                            "SOL" -> Sdk.solana.sendTransaction(
                                SolanaTxRequest(from = address, to = to.trim(), lamports = units.toString()),
                            )
                            "SUI" -> Sdk.sui.sendTransaction(
                                SuiTxRequest(from = address, to = to.trim(), mist = units.toString()),
                            )
                            "BTC" -> Sdk.btc.sendBitcoin(
                                fromAddress = address,
                                recipientAddress = to.trim(),
                                amountInSatoshis = units.toLong(),
                            )
                            else -> Sdk.evm.sendTransaction(
                                EvmTxRequest(from = address, to = to.trim(), value = units.toString()),
                            )
                        }
                        txHash = digest
                        if (chain == "SUI") {
                            suiStatus = "Waiting for Sui indexing…"
                            try {
                                var receipt = Sdk.sui.getTransactionStatus(digest)
                                for (attempt in 1 until 10) {
                                    if (receipt != null) break
                                    delay(1_000)
                                    receipt = Sdk.sui.getTransactionStatus(digest)
                                }
                                val indexed = receipt
                                suiStatus = when {
                                    indexed == null -> "Pending: digest was broadcast but is not indexed yet."
                                    indexed.status -> "Succeeded at checkpoint ${indexed.checkpoint ?: "pending"}."
                                    else -> "Failed on-chain: ${indexed.err ?: "unknown execution error"}"
                                }
                            } catch (e: Exception) {
                                suiStatus = "Status lookup failed: ${e.message ?: "unknown error"}"
                            }
                        }
                    } catch (e: DynamicException) {
                        snackbar.showSnackbar(e.message ?: "Send failed")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("native: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Sending…" else "Send transaction", style = AppTextStyles.ButtonLabel) }
        Spacer(Modifier.height(16.dp))
        txHash?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Broadcast", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Tx hash: $it", style = MaterialTheme.typography.bodyMedium)
                    suiStatus?.let { status ->
                        Spacer(Modifier.height(4.dp))
                        Text("Status: $status", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("txHash", it))
                        scope.launch { snackbar.showSnackbar("Tx hash copied") }
                    }) { Text("Copy tx hash") }
                }
            }
        }
    }
    StepUpHost(stepUp)
}
