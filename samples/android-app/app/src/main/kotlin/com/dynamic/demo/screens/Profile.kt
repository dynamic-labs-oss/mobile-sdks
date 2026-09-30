// Raw user JSON (verifiedCredentials included) + the full auth JWT — mirror
// of flutter-sdk/example's ProfileScreen "_valueCard" cards. That reference
// app shows these as the landing screen itself; here it's its own screen
// reached from Home, since this demo's Home is wallet-focused already.
// Also the entry point for the account-level (not per-wallet) new screens:
// Passkeys, MFA recovery codes, Step-Up Auth (standalone), Business Accounts.
//
// NOTE: the reference also shows a separate "Min Auth Token" — a genuinely
// distinct, shorter token pushed from the SDK's own webview/native bridge
// (auth_module.dart's minifiedTokenChanged store), not a display-side
// truncation of the full JWT. This generated SDK has no equivalent state
// field/bridge message yet, so that card isn't reproduced here — it would
// be new SDK surface (spec + all 4 native bridges), not a UI-only gap. Our
// own `authToken` is already the JS SDK's PREFERRED minifiedJwt (with a
// fallback to the legacy jwt) per auth.flows.ts — there's no separate
// "full" token being hidden here.
package com.dynamic.demo.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.Sdk
import com.dynamic.sdk.SdkUser
import kotlinx.coroutines.launch

@Composable
fun ProfileView(
    user: SdkUser,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPasskeys: () -> Unit,
    onMfaRecoveryCodes: () -> Unit,
    onStepUpAuth: () -> Unit,
    onBusinessAccounts: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userJson = remember(user) {
        kotlinx.serialization.json.Json { prettyPrint = true }
            .encodeToString(com.dynamic.sdk.SdkUser.serializer(), user)
    }
    val token = Sdk.client.auth.authToken ?: ""

    fun copy(value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("value", value))
        scope.launch { snackbar.showSnackbar("Copied to clipboard") }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Profile", style = AppTextStyles.SectionHeading)
        }
        Spacer(Modifier.height(16.dp))
        Text("Account", style = AppTextStyles.ListItemTitle)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPasskeys, modifier = Modifier.fillMaxWidth()) { Text("Passkeys") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onMfaRecoveryCodes, modifier = Modifier.fillMaxWidth()) { Text("MFA Recovery Codes") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onStepUpAuth, modifier = Modifier.fillMaxWidth()) { Text("Step-Up Auth") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBusinessAccounts, modifier = Modifier.fillMaxWidth()) { Text("Business Accounts") }
        Spacer(Modifier.height(24.dp))
        Text("User:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            Text(userJson, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { copy(userJson) }) { Text("Copy") }
        Spacer(Modifier.height(24.dp))
        Text("Token:", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            Text(token, modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { copy(token) }) { Text("Copy") }
    }
}
