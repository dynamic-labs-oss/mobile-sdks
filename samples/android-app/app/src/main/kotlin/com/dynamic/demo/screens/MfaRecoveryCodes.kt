// MFA recovery codes — auth.getMfaRecoveryCodes, createNewMfaRecoveryCodes,
// acknowledgeMfaRecoveryCodes, isPendingMfaRecoveryCodesAcknowledgment.
// Shows the current codes (monospace, grey-200 chip per DESIGN.md §1), a
// "Generate new codes" button, and an acknowledgment step gated behind
// isPendingMfaRecoveryCodesAcknowledgment before the screen is dismissible.
package com.dynamic.demo.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.sdk.DynamicException
import kotlinx.coroutines.launch

@Composable
fun MfaRecoveryCodesView(
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var codes by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Re-read after any mutating call — this is a plain property on
    // AuthClient (user.mfaBackupCodeAcknowledgement-derived), not a stream.
    var pendingAck by remember { mutableStateOf(Sdk.client.auth.isPendingMfaRecoveryCodesAcknowledgment) }

    suspend fun load() {
        loading = true
        error = null
        try {
            codes = Sdk.client.auth.getMfaRecoveryCodes().recoveryCodes
            pendingAck = Sdk.client.auth.isPendingMfaRecoveryCodesAcknowledgment
        } catch (e: DynamicException) {
            error = e.message ?: "Failed to load recovery codes"
        } catch (e: Exception) {
            error = "native: ${e.message}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun copy(value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("recovery codes", value))
        scope.launch { snackbar.showSnackbar("Copied to clipboard") }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Gated behind acknowledgment (CAPABILITIES.md: "before the codes
            // screen is dismissible") — hidden, not just disabled, while a
            // fresh set of codes hasn't been confirmed saved yet.
            if (!pendingAck) {
                TextButton(onClick = onBack) { Text("← Back") }
            }
            Text("MFA Recovery Codes", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TxtError))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { scope.launch { load() } }) { Text("Retry") }
                }
            }
            else -> {
                Text("Your recovery codes", style = AppTextStyles.ListItemTitle)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        codes.orEmpty().forEach { code ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                            ) {
                                Text(
                                    code,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    modifier = Modifier
                                        .background(AppColors.Grey200, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { copy(codes.orEmpty().joinToString("\n")) }) { Text("Copy all codes") }
                Spacer(Modifier.height(16.dp))
                AppButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                codes = Sdk.client.auth.createNewMfaRecoveryCodes().recoveryCodes
                                pendingAck = Sdk.client.auth.isPendingMfaRecoveryCodesAcknowledgment
                                snackbar.showSnackbar("Generated new recovery codes")
                            } catch (e: DynamicException) {
                                snackbar.showSnackbar(e.message ?: "Generate failed")
                            } catch (e: Exception) {
                                snackbar.showSnackbar("native: ${e.message}")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Generating…" else "Generate new codes", style = AppTextStyles.ButtonLabel) }

                if (pendingAck) {
                    Spacer(Modifier.height(16.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Please confirm you've saved these codes somewhere safe.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.Orange),
                            )
                            Spacer(Modifier.height(8.dp))
                            AppButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            Sdk.client.auth.acknowledgeMfaRecoveryCodes()
                                            pendingAck = false
                                        } catch (e: DynamicException) {
                                            snackbar.showSnackbar(e.message ?: "Acknowledge failed")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("I've saved my recovery codes", style = AppTextStyles.ButtonLabel) }
                        }
                    }
                }
            }
        }
    }
}
