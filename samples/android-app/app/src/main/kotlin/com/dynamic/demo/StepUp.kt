// Step-up (MFA) gate, mirroring the Flutter demo's step_up_modal.dart and the
// MAUI demo's StepUpHelper.cs: generic over whatever credentials the server
// reports for the scope — prefers an already-registered TOTP device, falls
// back to email-OTP reauth when the user has none (same has-MFA vs. no-MFA
// branch as dynamic-auth's usePromptStepUpAuth). Other credential kinds
// (passkey, SMS, social, wallet-signature) aren't wired up yet.
package com.dynamic.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.StepUpCredential
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * Awaits the step-up gate for [scope]: asks the server (checkStepUp), and if
 * required, renders a dialog (via [StepUpHost], which must be composed
 * alongside whatever calls this) letting the user complete it. Suspends
 * until the user verifies or cancels. Call from inside a coroutine, same as
 * any other suspend SDK call — no host-provided callback needed.
 */
class StepUpGate {
    var pending by mutableStateOf<Pair<String, List<StepUpCredential>>?>(null)
        private set
    private var result: CompletableDeferred<Boolean>? = null

    suspend fun ensure(scope: String): Boolean {
        val check = Sdk.client.auth.checkStepUp(scope)
        if (!check.isRequired) return true
        val deferred = CompletableDeferred<Boolean>()
        result = deferred
        pending = scope to check.credentials
        return deferred.await()
    }

    fun complete(success: Boolean) {
        pending = null
        result?.complete(success)
        result = null
    }
}

@Composable
fun rememberStepUpGate(): StepUpGate = remember { StepUpGate() }

/** Renders the step-up dialog when [gate] has a pending request — compose this once per screen that calls [StepUpGate.ensure]. */
@Composable
fun StepUpHost(gate: StepUpGate) {
    val (scope, credentials) = gate.pending ?: return
    StepUpDialog(scope = scope, credentials = credentials, onResult = gate::complete)
}

private enum class StepUpMethod { TOTP, EMAIL }

@Composable
private fun StepUpDialog(
    scope: String,
    credentials: List<StepUpCredential>,
    onResult: (Boolean) -> Unit,
) {
    val scopeState = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var emailVerificationUuid by remember { mutableStateOf<String?>(null) }

    fun isTotp(c: StepUpCredential) = c.type == "totp"
    fun isEmail(c: StepUpCredential) = c.format == "email"
    val method = when {
        credentials.any(::isTotp) -> StepUpMethod.TOTP
        credentials.any(::isEmail) -> StepUpMethod.EMAIL
        else -> null
    }
    fun label(c: StepUpCredential) = c.alias ?: when (c.type) {
        "totp" -> "Authenticator app (TOTP)"
        "passkey" -> "Passkey"
        else -> c.type ?: c.format ?: "Credential"
    }

    // Email reauth needs a code sent before it can be entered — auto-send on
    // first composition, same as the real widget.
    LaunchedEffect(Unit) {
        if (method == StepUpMethod.EMAIL) {
            busy = true
            try {
                emailVerificationUuid = Sdk.client.auth.sendStepUpEmailOtp().verificationUuid
            } catch (e: DynamicException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onResult(false) },
        title = { Text("Verify it's you") },
        text = {
            Column {
                Text("This action needs step-up authentication.")
                Spacer(Modifier.height(12.dp))
                credentials.forEach { credential ->
                    val supported = isTotp(credential) || isEmail(credential)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (supported) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(if (supported) label(credential) else "${label(credential)} — not in this demo")
                    }
                }
                if (method == StepUpMethod.EMAIL) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (emailVerificationUuid != null) "Code sent to your email." else "Sending a code to your email…")
                }
                if (method != null && (method == StepUpMethod.TOTP || emailVerificationUuid != null)) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(if (method == StepUpMethod.TOTP) "Authenticator code" else "Email code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else if (method == null) {
                    Text("No supported step-up method for this demo.")
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
                if (busy) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            if (method != null) {
                TextButton(
                    enabled = !busy && (method == StepUpMethod.TOTP || emailVerificationUuid != null),
                    onClick = {
                        scopeState.launch {
                            busy = true
                            error = null
                            try {
                                when (method) {
                                    StepUpMethod.TOTP -> Sdk.client.auth.completeTotpStepUp(code.trim(), scope)
                                    StepUpMethod.EMAIL -> Sdk.client.auth.completeEmailOtpStepUp(
                                        emailVerificationUuid!!, code.trim(), scope,
                                    )
                                }
                                onResult(true)
                            } catch (e: DynamicException) {
                                error = e.message
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text("Verify") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { onResult(false) }) { Text("Cancel") }
        },
    )
}
