// Passkeys management — auth.getPasskeys/registerPasskey/deletePasskey.
// List-item-row pattern from DESIGN.md §7: each passkey shows alias/device
// info, a destructive delete button; a "Register passkey" CTA at the top;
// loading/error/empty states per the standard pattern.
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.demo.StepUpHost
import com.dynamic.demo.rememberStepUpGate
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.UserPasskey
import kotlinx.coroutines.launch

@Composable
fun PasskeysView(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val stepUp = rememberStepUpGate()
    var passkeys by remember { mutableStateOf<List<UserPasskey>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            passkeys = Sdk.client.auth.getPasskeys()
        } catch (e: DynamicException) {
            error = e.message ?: "Failed to load passkeys"
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
            Text("Passkeys", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        AppButton(
            enabled = !registering,
            onClick = {
                registering = true
                scope.launch {
                    try {
                        Sdk.client.auth.registerPasskey()
                        snackbar.showSnackbar("Passkey registered")
                        reload()
                    } catch (e: DynamicException) {
                        snackbar.showSnackbar(e.message ?: "Register failed")
                    } catch (e: Exception) {
                        snackbar.showSnackbar("native: ${e.message}")
                    } finally {
                        registering = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (registering) "Registering…" else "Register passkey", style = AppTextStyles.ButtonLabel) }
        Spacer(Modifier.height(16.dp))

        when {
            loading -> Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
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
            passkeys.isNullOrEmpty() -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No passkeys configured", style = AppTextStyles.ListItemTitle)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Register one above to sign in without a password.",
                        style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
                    )
                }
            }
            else -> passkeys!!.forEach { passkey ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(passkey.alias ?: passkey.id, style = AppTextStyles.ListItemTitle)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "id: ${passkey.id.take(8)}…",
                            style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600),
                        )
                        Text(
                            "Created: ${passkey.createdAt}",
                            style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600),
                        )
                        passkey.userAgent?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(color = AppColors.Grey600))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        // Elevated scope 'credential:unlink' — step up first.
                                        if (!stepUp.ensure("credential:unlink")) return@launch
                                        Sdk.client.auth.deletePasskey(passkey.id)
                                        snackbar.showSnackbar("Passkey deleted")
                                        reload()
                                    } catch (e: DynamicException) {
                                        snackbar.showSnackbar(e.message ?: "Delete failed")
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar("native: ${e.message}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Red),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Delete") }
                    }
                }
            }
        }
    }
    StepUpHost(stepUp)
}
