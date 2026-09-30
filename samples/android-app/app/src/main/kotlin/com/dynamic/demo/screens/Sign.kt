// Sign Message — generic over every chain (wallet.signMessage(message) goes
// through the installed WalletSigner regardless of wallet.chain), so this
// already covers Sui/BTC wallets with no chain-branching needed here.
package com.dynamic.demo.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.StepUpHost
import com.dynamic.demo.rememberStepUpGate
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.launch

@Composable
fun SignView(
    wallet: Wallet,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Hello World") }
    var signature by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val stepUp = rememberStepUpGate()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Sign Message", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        AppButton(
            onClick = {
                busy = true
                signature = null
                scope.launch {
                    try {
                        // Step-up gate: checkStepUp → StepUpHost dialog to pick a
                        // method + validate a code → mints the wallet:sign elevated
                        // token. No-op (proceeds immediately) when not required.
                        if (!stepUp.ensure("wallet:sign")) return@launch
                        signature = wallet.signMessage(message)
                    } catch (e: DynamicException) {
                        snackbar.showSnackbar(e.message ?: "Sign failed")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("native: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Signing…" else "Sign", style = AppTextStyles.ButtonLabel) }
        Spacer(Modifier.height(16.dp))
        signature?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Signed Message", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    StepUpHost(stepUp)
}
