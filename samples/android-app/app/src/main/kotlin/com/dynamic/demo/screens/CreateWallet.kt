// Create Wallet as its own screen (promoted out of Home — see
// create_wallet_screen.dart, the reference this ports): chain picker
// (BTC/EVM/Solana/Sui), a "protect with a password" switch that reveals
// password + confirm fields when on, submit uses the pill/filled button
// style (DESIGN.md §5's one `FilledButton.icon` moment). Backs onto
// waas.createWallet(chain) plus, if the password switch is on,
// waas.setWaasWalletAccountPassword(...) right after creation — this SDK's
// createWallet has no inline `password:` parameter, unlike the richer
// flutter-sdk/example reference, so the two-call sequence CAPABILITIES.md
// describes is the real (and only) way to do this here.
package com.dynamic.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppPillButton
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.sdk.DynamicException
import kotlinx.coroutines.launch

@Composable
fun CreateWalletView(
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var chain by remember { mutableStateOf(CHAIN_OPTIONS.first()) }
    var chainMenu by remember { mutableStateOf(false) }
    var protectWithPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Create Wallet", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text("Chain", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { chainMenu = true }) { Text(chain) }
            DropdownMenu(expanded = chainMenu, onDismissRequest = { chainMenu = false }) {
                CHAIN_OPTIONS.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { chain = option; chainMenu = false })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Protect with a password", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Encrypts the key share behind a password of your choice.",
                    style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
                )
            }
            Switch(
                checked = protectWithPassword,
                onCheckedChange = {
                    protectWithPassword = it
                    if (!it) {
                        password = ""
                        confirmPassword = ""
                    }
                },
            )
        }
        if (protectWithPassword) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
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
        AppPillButton(
            enabled = !busy,
            onClick = {
                error = null
                success = null
                if (protectWithPassword && password.isEmpty()) {
                    error = "Enter a password, or turn the switch off."
                    return@AppPillButton
                }
                if (protectWithPassword && password != confirmPassword) {
                    error = "The two passwords do not match."
                    return@AppPillButton
                }
                busy = true
                scope.launch {
                    try {
                        val created = Sdk.waas.createWallet(chain)
                        if (protectWithPassword) {
                            Sdk.waas.setWaasWalletAccountPassword(chain, created.accountAddress, password)
                        }
                        success = "Created $chain wallet ${created.accountAddress}"
                        password = ""
                        confirmPassword = ""
                        protectWithPassword = false
                        onCreated()
                    } catch (e: DynamicException) {
                        error = "Create wallet failed: ${e.message}"
                    } catch (e: Exception) {
                        error = "Create wallet failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Creating…" else "+ Create wallet", style = AppTextStyles.ButtonLabel.copy(color = AppColors.BgWhite)) }
    }
}
