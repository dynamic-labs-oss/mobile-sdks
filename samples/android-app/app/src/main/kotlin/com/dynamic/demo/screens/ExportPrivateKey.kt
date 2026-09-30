package com.dynamic.demo.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.dynamic.sdk.exportPrivateKey
import kotlinx.coroutines.launch

@Composable
fun ExportPrivateKeyView(
    wallet: Wallet,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val stepUp = rememberStepUpGate()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Export Private Key", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your private key gives full control over this wallet. Dynamic reveals it " +
                "directly on screen — never to this app or its developer. Don't share it " +
                "or take a screenshot.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        AppButton(
            onClick = {
                busy = true
                done = false
                scope.launch {
                    try {
                        // Elevated-access gate, distinct scope from Sign/Send's
                        // "wallet:sign" — mirrors export_private_key_screen.dart.
                        if (!stepUp.ensure("wallet:export")) return@launch
                        // Wallet-scoped call — the object already in hand, no
                        // re-lookup — same shape as wallet.signMessage(...).
                        wallet.exportPrivateKey()
                        done = true
                    } catch (e: DynamicException) {
                        snackbar.showSnackbar(e.message ?: "Export failed")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("native: ${e.message}")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Revealing…" else "Reveal Private Key", style = AppTextStyles.ButtonLabel) }
        if (done) {
            Spacer(Modifier.height(16.dp))
            Text("Done — the key was shown once and is not stored here.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    StepUpHost(stepUp)
}
