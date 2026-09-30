// Login screen: email OTP (the only method this demo had before), plus
// (new) one button per enabled social provider, and the hidden 7-tap
// Developer Settings gesture on the logo — mirrors Flutter's/MAUI's
// SecretTapDetector exactly (7 taps inside a rolling 2-second window).
package com.dynamic.demo.screens

import android.content.Context
import android.util.Log
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import com.dynamic.sdk.ExternalWalletOption
import com.dynamic.sdk.externalWallet
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dynamic.demo.AppButton
import com.dynamic.demo.AppColors
import com.dynamic.demo.AppTextStyles
import com.dynamic.demo.R
import com.dynamic.demo.Sdk
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.SdkUser
import kotlinx.coroutines.launch

// SecretTapDetector: 7 taps on the logo, each no more than 2000ms after the
// previous one, opens Developer Settings. The window is measured PER GAP
// between consecutive taps, not against a fixed window start — verified
// against the reference (flutter-sdk/example's secret_tap_detector.dart /
// MAUI's SecretTapDetector.cs): 7 taps each ≤2s apart succeed even if the
// whole sequence takes much longer than 2s total. Any gap over 2000ms resets
// the count to start counting again from that tap.
private const val SECRET_TAP_COUNT = 7
private const val SECRET_TAP_RESET_AFTER_MS = 2000L

/**
 * The wallets this demo offers, so each one can be tested by hand. Mirrored
 * in the iOS, Flutter and MAUI demos.
 *
 * REGISTRY KEYS, not names, and that matters: a name match picks the wrong
 * wallet here. "Trust" also matches `ta` (TrustAssetApp) and "Bitget" also
 * matches `bgw`, whose link is a Telegram bot, not the app.
 *
 * Most connect over WalletConnect: each has sign_v2 and a mobile deep link,
 * so the tap opens that wallet straight away (see
 * ExternalWalletOption.connectionDeeplink).
 *
 * Phantom and Solflare have NEITHER, and go through the shared Solana
 * deep-link mechanism instead, which builds its own URL — see
 * SolanaDeepLinkWallet for the five values that differ between the two.
 *
 * Xverse is Bitcoin over WalletConnect's bip122 namespace. Its registry entry
 * has no link and no sign_v2, so the spec supplies both — see
 * BIP122_WALLET_LINKS in wallet-book.rules.ts.
 *
 * Coinbase is missing because the SDK cannot reach it: sign_v1 only, no deep
 * link.
 */
private val TEST_WALLET_KEYS = setOf(
    "metamask",
    "phantom",
    "solflare",
    "xverse",
    "trust",
    "okxwallet",
    "rabby",
    "rainbow",
    "backpack",
    "bitgetwallet",
    "exodus",
    "zerion",
)

@Composable
fun LoginView(
    onSubmit: (String) -> Unit,
    onSocialSignedIn: (SdkUser) -> Unit,
    onSocialLoginFailed: (String) -> Unit,
    onOpenDeveloperSettings: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableStateOf<Long?>(null) }
    var socialBusy by remember { mutableStateOf<String?>(null) }
    var externalJwt by remember { mutableStateOf("") }
    var byoaBusy by remember { mutableStateOf(false) }
    var walletBusy by remember { mutableStateOf(false) }
    var walletOptions by remember { mutableStateOf<List<ExternalWalletOption>>(emptyList()) }
    var showWalletPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Sdk.scope, NOT rememberCoroutineScope(): connecting a wallet and social
    // login both hand the user to another app and are answered after this
    // composable is gone — see Sdk.scope for what that costs.
    val scope = Sdk.scope
    // Provider.providerKind is ONE flat array shared by social login, SMS,
    // ramp, and account-abstraction (ZeroDev) providers alike (see the
    // generated Provider model's own doc) — not social-login-specific, so
    // this filters to entries actually usable as an OAuth social button:
    // not 'zerodev' and carrying a baseAuthUrl (signInWithSocial needs it
    // to build the provider's authorization URL; an SMS/ramp entry
    // wouldn't have one populated). Mirrors ios-app's socialProviders
    // filter exactly — same heuristic, Kotlin peer.
    val socialProviders = (Sdk.client.auth.socialProviders ?: emptyList())
        .filter { it.providerKind != "zerodev" && it.baseAuthUrl != null }

    fun onLogoTap() {
        val now = System.currentTimeMillis()
        val previous = lastTapMs
        if (previous == null || now - previous > SECRET_TAP_RESET_AFTER_MS) {
            tapCount = 0
        }
        lastTapMs = now
        tapCount += 1
        if (tapCount >= SECRET_TAP_COUNT) {
            tapCount = 0
            lastTapMs = null
            onOpenDeveloperSettings()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // The real Dynamic wordmark (pulled from the Dynamic Flutter SDK
        // example app's assets/dynamic-logo.png), same 200x100 sizing the
        // reference uses. Tappable for the hidden dev-settings gesture —
        // invisible in normal use, same as the reference.
        Image(
            painter = painterResource(R.drawable.dynamic_logo),
            contentDescription = "Dynamic",
            modifier = Modifier.height(100.dp).fillMaxWidth().clickable { onLogoTap() },
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(8.dp))
        Text("Headless Login", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(16.dp))
        AppButton(onClick = { onSubmit(email.trim()) }, modifier = Modifier.fillMaxWidth()) {
            Text("Send code", style = com.dynamic.demo.AppTextStyles.ButtonLabel)
        }

        if (socialProviders.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            // Section separator ("OR" divider) — DESIGN.md §7.
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = AppColors.BgBase4)
                Text(
                    "  OR  ",
                    style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
                )
                HorizontalDivider(Modifier.weight(1f), color = AppColors.BgBase4)
            }
            Spacer(Modifier.height(16.dp))
            socialProviders.forEach { provider ->
                val busy = socialBusy == provider.providerKind
                AppButton(
                    onClick = {
                        socialBusy = provider.providerKind
                        scope.launch {
                            try {
                                val user = Sdk.client.auth.signInWithSocial(provider.providerKind)
                                onSocialSignedIn(user)
                            } catch (e: DynamicException) {
                                onSocialLoginFailed(e.message ?: "Social login failed")
                            } catch (e: Exception) {
                                onSocialLoginFailed("native: ${e.message}")
                            } finally {
                                socialBusy = null
                            }
                        }
                    },
                    enabled = socialBusy == null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (provider.providerKind == "google") {
                            Image(
                                painter = painterResource(R.drawable.google_logo),
                                contentDescription = null,
                                modifier = Modifier.height(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (busy) "Signing in…" else "Continue with ${provider.providerKind.replaceFirstChar { it.uppercase() }}",
                            style = com.dynamic.demo.AppTextStyles.ButtonLabel,
                        )
                    }
                }
            }
        }

        // BYOA (bring-your-own-auth): exchange a JWT the HOST app's own
        // identity provider issued for a Dynamic session, instead of Dynamic
        // owning the credential. No externalUserId field — the JWT's `sub`
        // claim IS the external user id (signInWithExternalJwt's own doc).
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = AppColors.BgBase4)
            Text(
                "  OR  ",
                style = MaterialTheme.typography.bodySmall.copy(color = AppColors.TxtSecondary),
            )
            HorizontalDivider(Modifier.weight(1f), color = AppColors.BgBase4)
        }
        // External wallet (MetaMask, ...) over WalletConnect. The picker comes
        // from the SDK's own wallet registry; picking one opens THAT wallet,
        // while "Other wallet" leaves the choice to the OS.
        Spacer(Modifier.height(16.dp))
        AppButton(
            onClick = {
                walletBusy = true
                scope.launch {
                    try {
                        val options = runCatching {
                            Sdk.client.externalWallet.listExternalWallets()
                        }.getOrDefault(emptyList())
                        if (options.isEmpty()) {
                            signInWithWallet(context, null, onSocialSignedIn, onSocialLoginFailed)
                        } else {
                            walletOptions = options
                            showWalletPicker = true
                        }
                    } finally {
                        walletBusy = false
                    }
                }
            },
            enabled = !walletBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (walletBusy) "Connecting…" else "Continue with a wallet")
        }

        if (showWalletPicker) {
            // A real picker shows the head of the registry above a search
            // field. This demo shows a FIXED shortlist instead, so every
            // wallet worth testing by hand is one tap away: the registry
            // order buries Exodus at 154 and Zerion at 509, and any "top N"
            // cut hides them.
            AlertDialog(
                onDismissRequest = { showWalletPicker = false },
                title = { Text("Connect a wallet") },
                confirmButton = {},
                text = {
                    Column {
                        for (option in walletOptions.filter { it.key in TEST_WALLET_KEYS }) {
                            AppButton(
                                onClick = {
                                    showWalletPicker = false
                                    walletBusy = true
                                    scope.launch {
                                        try {
                                            signInWithWallet(
                                                context,
                                                option.key,
                                                onSocialSignedIn,
                                                onSocialLoginFailed,
                                                option.chain,
                                            )
                                        } finally {
                                            walletBusy = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("${option.name} (${option.chain})") }
                            Spacer(Modifier.height(4.dp))
                        }
                        AppButton(
                            onClick = {
                                showWalletPicker = false
                                walletBusy = true
                                scope.launch {
                                    try {
                                        signInWithWallet(context, null, onSocialSignedIn, onSocialLoginFailed)
                                    } finally {
                                        walletBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Other wallet (scan a QR code)") }
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = externalJwt,
            onValueChange = { externalJwt = it },
            label = { Text("External JWT") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            onClick = {
                byoaBusy = true
                scope.launch {
                    try {
                        val user = Sdk.client.auth.signInWithExternalJwt(externalJwt.trim())
                        onSocialSignedIn(user)
                    } catch (e: DynamicException) {
                        onSocialLoginFailed(e.message ?: "External JWT sign-in failed")
                    } catch (e: Exception) {
                        onSocialLoginFailed("native: ${e.message}")
                    } finally {
                        byoaBusy = false
                    }
                }
            },
            enabled = !byoaBusy && externalJwt.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (byoaBusy) "Signing in…" else "Sign in with external JWT", style = AppTextStyles.ButtonLabel)
        }
    }
}

/**
 * Sign-in with a wallet the user already has. [walletKey] is the picked
 * wallet's registry key, or null for "any wallet": with a key, the handle
 * carries that wallet's own deep link, so one tap opens the right app instead
 * of an OS-wide chooser.
 *
 * The SDK returns what to present and does NOT open anything itself — the
 * same split every platform makes, because presenting is per-platform.
 */
private suspend fun signInWithWallet(
    context: Context,
    walletKey: String?,
    onSignedIn: (SdkUser) -> Unit,
    onFailed: (String) -> Unit,
    // The picked wallet's OWN chain, or null for "any wallet".
    //
    // ONE namespace per connect, which is what dynamic-js-sdk does — it has
    // two separate connect functions (connectWithWalletConnectEvm proposes
    // only eip155, connectWithWalletConnectSolana only solana) rather than
    // one call proposing both. Asking an EVM-only wallet for a `solana`
    // namespace it never advertised is what this avoids: WalletConnect calls
    // those namespaces OPTIONAL, but a wallet is free to reject the whole
    // proposal, and Rainbow (chains: eip155 only) does.
    chain: String? = null,
) {
    try {
        val handle = Sdk.client.externalWallet.beginExternalWalletConnect(
            if (chain != null) listOf(chain) else listOf("EVM", "SOL"),
            walletKey,
        )
        // A null uri means this mechanism needs nothing presented. On a phone
        // there is always a wallet app to hand it to, so it is opened rather
        // than shown as a QR code.
        handle.uri?.let { uri ->
            // TEMPORARY diagnostic: the shape of the pairing URI, not its
            // contents. NAMES only — symKey is a live connection secret. A
            // wallet that opens and then shows nothing has not received the
            // session proposal, and what the wc: URI carries (or omits) is
            // the only part of that exchange visible from this side.
            runCatching {
                val outer = Uri.parse(uri)
                val inner = Uri.parse(outer.getQueryParameter("uri") ?: uri)
                android.util.Log.i(
                    "DynamicDemo",
                    "pairing: outer=${outer.scheme}://${outer.host}${outer.path} " +
                        "inner.scheme=${inner.scheme} " +
                        "inner.params=${inner.query?.split("&")?.map { it.substringBefore("=") }?.sorted()}",
                )
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
        val accounts = Sdk.client.externalWallet.awaitExternalWalletConnection()
        val account = accounts.firstOrNull()
        if (account == null) {
            onFailed("Wallet approved the connection but exposed no accounts")
            return
        }
        onSignedIn(Sdk.client.externalWallet.signInWithExternalWallet(account.address))
    } catch (e: DynamicException) {
        // Also to logcat: a snackbar truncates, and an api error's reason is
        // the whole point when a wallet sign-in fails.
        Log.e("DynamicDemo", "wallet sign-in failed", e)
        onFailed(e.message ?: "Wallet sign-in failed")
    } catch (e: Exception) {
        Log.e("DynamicDemo", "wallet sign-in failed (native)", e)
        onFailed("native: ${e.message}")
    }
}
