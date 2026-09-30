// Step-Up Auth — standalone entry point. The step-up MODAL mechanism
// already exists (StepUp.kt's StepUpGate/StepUpHost, triggered inline by
// Sign/Send/Export/SignTypedData) — this screen just lets a user proactively
// trigger it for an arbitrary scope, without first attempting a privileged
// action, matching the reference's dedicated screen.
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
import com.dynamic.demo.StepUpHost
import com.dynamic.demo.rememberStepUpGate
import com.dynamic.sdk.DynamicException
import kotlinx.coroutines.launch

@Composable
fun StepUpScreenView(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stepUp = rememberStepUpGate()
    var authScope by remember { mutableStateOf("wallet:sign") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Step-Up Auth", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text("Scope", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = authScope,
            onValueChange = { authScope = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = {
                busy = true
                result = null
                scope.launch {
                    try {
                        val check = Sdk.client.auth.checkStepUp(authScope.trim())
                        result = "isRequired=${check.isRequired}, credentials=${check.credentials.size}"
                    } catch (e: DynamicException) {
                        result = "Check failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check Step-Up Required") }
        Spacer(Modifier.height(8.dp))
        AppButton(
            enabled = !busy,
            onClick = {
                busy = true
                result = null
                scope.launch {
                    try {
                        val completed = stepUp.ensure(authScope.trim())
                        result = if (completed) "Step-up complete." else "Step-up cancelled."
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Prompt Step-Up Auth", style = AppTextStyles.ButtonLabel) }
        result?.let {
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    StepUpHost(stepUp)
}
