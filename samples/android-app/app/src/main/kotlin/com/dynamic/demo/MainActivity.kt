// Compose demo of the GENERATED Kotlin SDK, styled after the Flutter demo:
// Splash → Login (email/social/dev-gesture) → OTP → Home (wallet list +
// create/import + sign/send) → per-wallet + per-account screens. All SDK
// wiring lives in com.dynamic.sdk.android; this app just consumes
// Sdk.client. Individual screen composables live under screens/ (task #8 —
// this file only holds the Activity/navigation shell now, not every screen).
package com.dynamic.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dynamic.demo.screens.BusinessAccountDetailView
import com.dynamic.demo.screens.BusinessAccountsListView
import com.dynamic.demo.screens.CreateWalletView
import com.dynamic.demo.screens.DeveloperSettingsView
import com.dynamic.demo.screens.ExportPrivateKeyView
import com.dynamic.demo.screens.HomeView
import com.dynamic.demo.screens.ImportPrivateKeyView
import com.dynamic.demo.screens.LoginView
import com.dynamic.demo.screens.MfaRecoveryCodesView
import com.dynamic.demo.screens.OtpView
import com.dynamic.demo.screens.PasskeysView
import com.dynamic.demo.screens.ProfileView
import com.dynamic.demo.screens.SendView
import com.dynamic.demo.screens.SignTypedDataView
import com.dynamic.demo.screens.SignView
import com.dynamic.demo.screens.SplashView
import com.dynamic.demo.screens.StepUpScreenView
import com.dynamic.demo.screens.WalletDetailsView
import com.dynamic.demo.screens.WalletPasswordView
import com.dynamic.sdk.DynamicException
import com.dynamic.sdk.SdkUser
import com.dynamic.sdk.Wallet
import kotlinx.coroutines.launch

// Foreground-activity holder for the SDK (WaaS WebView + OAuth tab need it).
object ActivityHolder {
    var current: Activity? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHolder.current = this
        Sdk.build(applicationContext) { ActivityHolder.current }
        setContent { DynamicTheme { App() } }
        // A wallet's answer can arrive as the LAUNCH intent, not only through
        // onNewIntent: Android delivers it here whenever it creates a fresh
        // activity instance for the link instead of reusing the live one, and
        // singleTop does not prevent that. Without this the link is dropped
        // silently — the app just comes to the foreground and nothing
        // happens, which is indistinguishable from a wallet that never
        // answered. Measured with Solflare on a Pixel 8a.
        //
        // Only when it carries data: a plain launcher intent has none, and
        // handing that to the SDK's OAuth handling would be noise. Built
        // AFTER Sdk.build above, so the mechanisms exist to claim it.
        if (intent?.data != null) Sdk.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ActivityHolder.current = this
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Sdk.onNewIntent(intent)
    }
}

private sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object DeveloperSettings : Screen
    data class Otp(val verificationUuid: String, val email: String) : Screen
    data class Home(val user: SdkUser) : Screen
    // Carries the real generated Wallet model (client.wallets.userWallets),
    // not raw chain/address strings — so Details/Sign/Export call straight
    // through to wallet.exportPrivateKey()/wallet.signMessage(...) with no
    // re-lookup by address once we're here; the object in hand IS the wallet.
    data class WalletDetails(val user: SdkUser, val wallet: Wallet) : Screen
    data class Sign(val user: SdkUser, val wallet: Wallet) : Screen
    data class SignTypedData(val user: SdkUser, val wallet: Wallet) : Screen
    data class Send(val user: SdkUser, val wallet: Wallet) : Screen
    data class ExportKey(val user: SdkUser, val wallet: Wallet) : Screen
    data class WalletPassword(val user: SdkUser, val wallet: Wallet) : Screen
    data class Profile(val user: SdkUser) : Screen
    data class CreateWallet(val user: SdkUser) : Screen
    data class ImportPrivateKey(val user: SdkUser) : Screen
    data class Passkeys(val user: SdkUser) : Screen
    data class MfaRecoveryCodes(val user: SdkUser) : Screen
    data class StepUpStandalone(val user: SdkUser) : Screen
    data class BusinessAccounts(val user: SdkUser) : Screen
    data class BusinessAccountDetail(val user: SdkUser, val businessAccountId: String) : Screen
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // "Go to your wallet." Over WalletConnect a signature request travels
    // down an already-open relay session with no deep link of its own, so
    // without this prompt the app just looks frozen while the wallet waits
    // off-screen. Collected here, at the root, because every screen that
    // signs with an external wallet needs it — login included.
    LaunchedEffect(Unit) {
        Sdk.client.externalWalletActionRequested.collect { request ->
            val what = if (request.action == "signTransaction") "transaction" else "signature"
            snackbar.showSnackbar("Open ${request.walletName ?: "your wallet"} to approve the $what.")
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            when (val current = screen) {
                is Screen.Splash -> {
                    SplashView()
                    LaunchedEffect(Unit) {
                        try {
                            Sdk.client.initialize()
                            val token = Sdk.client.auth.authToken
                            screen = if (token != null) {
                                val user = Sdk.client.auth.refreshUser()
                                // No explicit auto-create-wallets call needed: WaasExtension
                                // subscribes to userChanged and syncs missing wallets in the
                                // background — mirrors dynamic-auth's useSyncDynamicWaas.
                                Screen.Home(user)
                            } else {
                                Screen.Login
                            }
                        } catch (e: DynamicException) {
                            snackbar.showSnackbar("Init failed: ${e.message}")
                            screen = Screen.Login
                        }
                    }
                }

                is Screen.Login -> LoginView(
                    onSubmit = { email ->
                        scope.launch {
                            try {
                                val sent = Sdk.client.auth.sendEmailOtp(email)
                                screen = Screen.Otp(sent.verificationUuid, sent.email)
                            } catch (e: DynamicException) {
                                snackbar.showSnackbar(e.message ?: "Login failed")
                            }
                        }
                    },
                    onSocialSignedIn = { user -> screen = Screen.Home(user) },
                    onSocialLoginFailed = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    onOpenDeveloperSettings = { screen = Screen.DeveloperSettings },
                )

                is Screen.DeveloperSettings -> DeveloperSettingsView(onBack = { screen = Screen.Login })

                is Screen.Otp -> OtpView(current.email) { code ->
                    scope.launch {
                        try {
                            Sdk.client.auth.verifyEmailOtp(current.verificationUuid, code)
                            val user = Sdk.client.auth.refreshUser()
                            // No explicit auto-create-wallets call needed — see the
                            // Splash branch's comment.
                            screen = Screen.Home(user)
                        } catch (e: DynamicException) {
                            snackbar.showSnackbar(e.message ?: "Verification failed")
                        }
                    }
                }

                is Screen.Home -> HomeView(
                    user = current.user,
                    snackbar = snackbar,
                    onUserChanged = { screen = Screen.Home(it) },
                    onDetails = { wallet -> screen = Screen.WalletDetails(current.user, wallet) },
                    onSign = { wallet -> screen = Screen.Sign(current.user, wallet) },
                    onSend = { wallet -> screen = Screen.Send(current.user, wallet) },
                    onProfile = { screen = Screen.Profile(current.user) },
                    onCreateWallet = { screen = Screen.CreateWallet(current.user) },
                    onImportPrivateKey = { screen = Screen.ImportPrivateKey(current.user) },
                    onSignOut = {
                        scope.launch {
                            runCatching { Sdk.client.auth.signOut() }
                            screen = Screen.Login
                        }
                    },
                )

                is Screen.CreateWallet -> CreateWalletView(
                    onBack = { screen = Screen.Home(current.user) },
                    onCreated = { screen = Screen.Home(current.user) },
                )

                is Screen.ImportPrivateKey -> ImportPrivateKeyView(
                    onBack = { screen = Screen.Home(current.user) },
                    onImported = { screen = Screen.Home(current.user) },
                )

                is Screen.Profile -> ProfileView(
                    user = current.user,
                    snackbar = snackbar,
                    onBack = { screen = Screen.Home(current.user) },
                    onPasskeys = { screen = Screen.Passkeys(current.user) },
                    onMfaRecoveryCodes = { screen = Screen.MfaRecoveryCodes(current.user) },
                    onStepUpAuth = { screen = Screen.StepUpStandalone(current.user) },
                    onBusinessAccounts = { screen = Screen.BusinessAccounts(current.user) },
                )

                is Screen.Passkeys -> PasskeysView(
                    snackbar = snackbar,
                    onBack = { screen = Screen.Profile(current.user) },
                )

                is Screen.MfaRecoveryCodes -> MfaRecoveryCodesView(
                    snackbar = snackbar,
                    onBack = { screen = Screen.Profile(current.user) },
                )

                is Screen.StepUpStandalone -> StepUpScreenView(onBack = { screen = Screen.Profile(current.user) })

                is Screen.BusinessAccounts -> BusinessAccountsListView(
                    snackbar = snackbar,
                    onBack = { screen = Screen.Profile(current.user) },
                    onOpen = { businessAccountId -> screen = Screen.BusinessAccountDetail(current.user, businessAccountId) },
                )

                is Screen.BusinessAccountDetail -> BusinessAccountDetailView(
                    businessAccountId = current.businessAccountId,
                    snackbar = snackbar,
                    onBack = { screen = Screen.BusinessAccounts(current.user) },
                    // transferBusinessAccountOwnership tears down the local
                    // session server-side — exit all the way to Login,
                    // mirroring what signOut does elsewhere in this file.
                    onSessionEnded = { screen = Screen.Login },
                )

                is Screen.WalletDetails -> WalletDetailsView(
                    wallet = current.wallet,
                    snackbar = snackbar,
                    onBack = { screen = Screen.Home(current.user) },
                    onSign = { screen = Screen.Sign(current.user, current.wallet) },
                    onSignTypedData = { screen = Screen.SignTypedData(current.user, current.wallet) },
                    onSend = { screen = Screen.Send(current.user, current.wallet) },
                    onExportKey = { screen = Screen.ExportKey(current.user, current.wallet) },
                    onWalletPassword = { screen = Screen.WalletPassword(current.user, current.wallet) },
                )

                is Screen.Sign -> SignView(
                    wallet = current.wallet,
                    snackbar = snackbar,
                    // Reachable both from Home's wallet card and from Wallet
                    // Details — back to Home either way, same as before this
                    // pass (no navigation stack; Home always has this wallet).
                    onBack = { screen = Screen.Home(current.user) },
                )

                is Screen.SignTypedData -> SignTypedDataView(
                    wallet = current.wallet,
                    snackbar = snackbar,
                    // Only reachable from Wallet Details (EVM-only action).
                    onBack = { screen = Screen.WalletDetails(current.user, current.wallet) },
                )

                is Screen.Send -> SendView(
                    wallet = current.wallet,
                    snackbar = snackbar,
                    onBack = { screen = Screen.Home(current.user) },
                )

                is Screen.ExportKey -> ExportPrivateKeyView(
                    wallet = current.wallet,
                    snackbar = snackbar,
                    onBack = { screen = Screen.Home(current.user) },
                )

                is Screen.WalletPassword -> WalletPasswordView(
                    wallet = current.wallet,
                    onBack = { screen = Screen.WalletDetails(current.user, current.wallet) },
                )
            }
        }
    }
}
