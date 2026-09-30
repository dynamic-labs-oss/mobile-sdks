// Hidden developer-settings screen, reached by 7-tapping the Login logo
// within a rolling 2s window (see Login.kt's SecretTapDetector port). Lets a
// dev override environmentId/apiBaseUrl and restart the app with the new
// settings — mirrors Flutter's developer_settings_screen.dart /
// MAUI's DeveloperSettingsPage, adapted to this dsl repo's simpler
// DynamicClient (environmentId + apiBaseUrl are its only two dev-facing
// overrides — no webviewUrl/migration-modal flag exist on this surface).
//
// "Restart" here means a genuine process restart (relaunch the launcher
// Activity, then exit this process) rather than Flutter's in-place
// KeyedSubtree remount or MAUI's root-page swap — the most robust option
// available on Android, and it trivially guarantees every singleton (Sdk,
// the booted WaaS engine, ActivityHolder) rebuilds clean with zero special
// teardown code, since [Sdk.build] is a normal cold-start path already.
package com.dynamic.demo.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.DevSettings

private const val PRODUCTION_ENVIRONMENT_ID = "3e219b76-dcf1-40ab-aad6-652c4dfab4cc"
private const val PRODUCTION_API_BASE_URL = "https://app.dynamicauth.com/api/v0"

@Composable
fun DeveloperSettingsView(onBack: () -> Unit) {
    val context = LocalContext.current
    var environmentId by remember { mutableStateOf(DevSettings.environmentId(context) ?: PRODUCTION_ENVIRONMENT_ID) }
    var apiBaseUrl by remember { mutableStateOf(DevSettings.apiBaseUrl(context) ?: PRODUCTION_API_BASE_URL) }
    var error by remember { mutableStateOf<String?>(null) }

    fun restartProcess() {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
        context.startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Developer Settings", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Overrides are applied on next app start. \"Apply & Restart\" restarts the app process immediately.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Text("Environment ID", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = environmentId,
            onValueChange = { environmentId = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("API Base URL", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = apiBaseUrl,
            onValueChange = { apiBaseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall.copy(color = com.dynamic.demo.AppColors.TxtError))
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            onClick = {
                if (environmentId.isBlank() || apiBaseUrl.isBlank()) {
                    error = "Environment ID and API Base URL are both required."
                    return@AppButton
                }
                DevSettings.save(context, environmentId.trim(), apiBaseUrl.trim())
                restartProcess()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply & Restart", style = AppTextStyles.ButtonLabel) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                DevSettings.save(context, null, null)
                environmentId = PRODUCTION_ENVIRONMENT_ID
                apiBaseUrl = PRODUCTION_API_BASE_URL
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset to defaults") }
    }
}
