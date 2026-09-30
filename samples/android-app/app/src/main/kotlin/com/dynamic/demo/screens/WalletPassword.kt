// Wallet password management — reachable from Wallet Details. Four
// independent sections sharing one busy/error/success trio (mirrors the
// reference's wallet_password_screen.dart layout): check recovery state,
// unlock, set a first password, change an existing one.
package com.dynamic.demo.screens

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.launch

@Composable
fun WalletPasswordView(
    wallet: Wallet,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    var unlockPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var existingPassword by remember { mutableStateOf("") }
    var updatedPassword by remember { mutableStateOf("") }

    fun run(label: String, action: suspend () -> String) {
        status = null
        isError = false
        busy = true
        scope.launch {
            try {
                status = action()
            } catch (e: DynamicException) {
                status = "$label failed: ${e.message}"
                isError = true
            } catch (e: Exception) {
                status = "$label failed: ${e.message}"
                isError = true
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Wallet Password", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        status?.let {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(color = if (isError) AppColors.TxtError else AppColors.Green),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("Check Recovery State", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                run("Check recovery state") {
                    val state = Sdk.waas.getWalletRecoveryState(wallet.chain, wallet.address)
                    "walletReadyState=${state.walletReadyState}, isPasswordEncrypted=${state.isPasswordEncrypted}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check Recovery State", style = AppTextStyles.ButtonLabel) }

        Spacer(Modifier.height(24.dp))
        Text("Unlock Wallet", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = unlockPassword,
            onValueChange = { unlockPassword = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                if (unlockPassword.isEmpty()) {
                    status = "Enter a password to unlock."
                    isError = true
                    return@AppButton
                }
                run("Unlock") {
                    Sdk.waas.unlockWallet(wallet.chain, wallet.address, unlockPassword)
                    "Wallet unlocked for this session."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Unlock Wallet", style = AppTextStyles.ButtonLabel) }

        Spacer(Modifier.height(24.dp))
        Text("Set Password", style = AppTextStyles.ListItemTitle)
        Text(
            "For a wallet that doesn't have one yet.",
            style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                if (newPassword.isEmpty()) {
                    status = "Enter a new password."
                    isError = true
                    return@AppButton
                }
                run("Set password") {
                    Sdk.waas.setWaasWalletAccountPassword(wallet.chain, wallet.address, newPassword)
                    "Password set."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set Password", style = AppTextStyles.ButtonLabel) }

        Spacer(Modifier.height(24.dp))
        Text("Update Password", style = AppTextStyles.ListItemTitle)
        Text(
            "For a wallet that already has one.",
            style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = existingPassword,
            onValueChange = { existingPassword = it },
            label = { Text("Existing password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = updatedPassword,
            onValueChange = { updatedPassword = it },
            label = { Text("New password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                if (existingPassword.isEmpty() || updatedPassword.isEmpty()) {
                    status = "Enter both the existing and new password."
                    isError = true
                    return@AppButton
                }
                run("Update password") {
                    Sdk.waas.updateWaasPassword(wallet.chain, wallet.address, existingPassword, updatedPassword)
                    "Password updated."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Update Password", style = AppTextStyles.ButtonLabel) }
    }
}
