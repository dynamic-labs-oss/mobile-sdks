// Sign Typed Data (EVM only) — same skeleton as Sign Message (message/JSON
// field → step-up-gated Sign → result card), but only ever offered from an
// EVM wallet's Wallet Details screen (see WalletDetails.kt). Backs onto
// waas.signTypedData(chain, accountAddress, typedData) — the caller
// pre-serializes the full EIP-712 JSON object (domain/types/message/
// primaryType); the DSL has no generic map/object IR type, so unlike a
// structured param this mirrors how signTransaction already takes a
// pre-serialized blob.
package com.dynamic.demo.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.demo.StepUpHost
import com.dynamic.demo.rememberStepUpGate
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.launch

private val EXAMPLE_TYPED_DATA = """
{
  "types": {
    "EIP712Domain": [
      {"name": "name", "type": "string"},
      {"name": "version", "type": "string"},
      {"name": "chainId", "type": "uint256"}
    ],
    "Person": [
      {"name": "name", "type": "string"},
      {"name": "wallet", "type": "address"}
    ]
  },
  "primaryType": "Person",
  "domain": {"name": "Dynamic Demo", "version": "1", "chainId": 11155111},
  "message": {"name": "Alice", "wallet": "0x0000000000000000000000000000000000000000"}
}
""".trimIndent()

@Composable
fun SignTypedDataView(
    wallet: Wallet,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var typedData by remember { mutableStateOf(EXAMPLE_TYPED_DATA) }
    var signature by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val stepUp = rememberStepUpGate()

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Sign Typed Data", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = typedData,
            onValueChange = { typedData = it },
            label = { Text("EIP-712 typed data (JSON)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
        )
        Spacer(Modifier.height(16.dp))
        AppButton(
            onClick = {
                busy = true
                signature = null
                scope.launch {
                    try {
                        if (!stepUp.ensure("wallet:sign")) return@launch
                        signature = Sdk.waas.signTypedData("EVM", wallet.address, typedData)
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
        ) { Text(if (busy) "Signing…" else "Sign Typed Data", style = AppTextStyles.ButtonLabel) }
        Spacer(Modifier.height(16.dp))
        signature?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Signed!", style = MaterialTheme.typography.bodyLarge.copy(color = AppColors.Green))
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    StepUpHost(stepUp)
}
