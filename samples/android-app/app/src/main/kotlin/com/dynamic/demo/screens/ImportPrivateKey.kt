// Import Private Key — waas.importPrivateKey(chain, privateKey, isRawScalarImport?).
// Chain picker (BTC/EVM/Solana/Sui), private-key text field, submit.
// isRawScalarImport only applies to SOL/SUI (ed25519 chains) — exposed as an
// optional toggle only when one of those chains is selected.
package com.dynamic.demo.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.dynamic.sdk.DynamicException
import kotlinx.coroutines.launch

private val ED25519_CHAINS = setOf("SOL", "SUI")

@Composable
fun ImportPrivateKeyView(
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var chain by remember { mutableStateOf(CHAIN_OPTIONS.first()) }
    var chainMenu by remember { mutableStateOf(false) }
    var privateKey by remember { mutableStateOf("") }
    var isRawScalarImport by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Import Private Key", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text("Chain", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { chainMenu = true }) { Text(chain) }
            DropdownMenu(expanded = chainMenu, onDismissRequest = { chainMenu = false }) {
                CHAIN_OPTIONS.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { chain = option; chainMenu = false; if (chain !in ED25519_CHAINS) isRawScalarImport = false })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = privateKey,
            onValueChange = { privateKey = it },
            label = { Text("Private key") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (chain in ED25519_CHAINS) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRawScalarImport, onCheckedChange = { isRawScalarImport = it })
                Text(
                    "Raw ed25519 scalar (e.g. exported from an external MPC system), not a seed",
                    style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TxtError))
        }
        success?.let {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.Green))
            }
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                if (privateKey.isBlank()) {
                    error = "Enter a private key to import."
                    return@AppButton
                }
                error = null
                success = null
                busy = true
                scope.launch {
                    try {
                        val imported = Sdk.waas.importPrivateKey(
                            chain,
                            privateKey.trim(),
                            if (chain in ED25519_CHAINS) isRawScalarImport else null,
                        )
                        success = "Imported $chain wallet ${imported.accountAddress}"
                        privateKey = ""
                        onImported()
                    } catch (e: DynamicException) {
                        error = "Import failed: ${e.message}"
                    } catch (e: Exception) {
                        error = "Import failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Importing…" else "Import", style = AppTextStyles.ButtonLabel) }
    }
}
