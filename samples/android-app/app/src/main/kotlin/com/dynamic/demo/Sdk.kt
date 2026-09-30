// App-wide SDK holder (mirror of Sdk.instance in the Flutter demo). The app
// only CONSUMES the SDK: platform-service wiring (adapters, HttpService) lives
// in the Kotlin SDK assembly (com.dynamic.sdk.android) — here we call
// createDynamicClient, then opt into the extensions this demo uses (WaaS, EVM).
package com.dynamic.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.dynamic.sdk.android.DynamicSdk
import com.dynamic.sdk.android.exportprivatekey.useExportPrivateKey
import com.dynamic.sdk.android.waas.useWaas
import com.dynamic.sdk.BtcClient
import com.dynamic.sdk.btc
import com.dynamic.sdk.btc.useBtc
import com.dynamic.sdk.businessaccount.useBusinessAccount
import com.dynamic.sdk.evm.EvmExtension
import com.dynamic.sdk.evm.evm
import com.dynamic.sdk.evm.useEvm
import com.dynamic.sdk.earn.useEarn
import com.dynamic.sdk.externalwallet.ReownExternalWalletProvider
import com.dynamic.sdk.ExternalWalletExtension
import com.dynamic.sdk.externalwallet.phantom.PhantomMechanism
import com.dynamic.sdk.externalwallet.phantom.SolflareMechanism
import com.dynamic.sdk.BtcNetwork
import com.dynamic.sdk.BusinessAccountClient
import com.dynamic.sdk.DynamicClient
import com.dynamic.sdk.EvmNetwork
import com.dynamic.sdk.ExportPrivateKeyClient
import com.dynamic.sdk.SolanaNetwork
import com.dynamic.sdk.SuiNetwork
import com.dynamic.sdk.StellarNetwork
import com.dynamic.sdk.TonNetwork
import com.dynamic.sdk.WaasClient
import com.dynamic.sdk.businessAccount
import com.dynamic.sdk.exportPrivateKey
import com.dynamic.sdk.waas
import com.dynamic.sdk.solana.SolanaExtension
import com.dynamic.sdk.solana.solana
import com.dynamic.sdk.solana.useSolana
import com.dynamic.sdk.stellar.useStellar
import com.dynamic.sdk.ton.useTon
import com.dynamic.sdk.legacywalletupgrade.useLegacyWalletUpgrade
import com.dynamic.sdk.platform.DynamicCaptchaWidget
import com.dynamic.sdk.zerodev.useZerodev
import com.dynamic.sdk.SuiClient
import com.dynamic.sdk.sui
import com.dynamic.sdk.sui.useSui

/**
 * Developer-settings overrides (see screens/DeveloperSettings.kt, reached by
 * 7-tapping the Login logo within 2s — mirrors Flutter's/MAUI's
 * SecretTapDetector). Persisted in plain SharedPreferences (not secure
 * storage — this is dev-only environment plumbing, never key material) and
 * read once at process start, before [Sdk.build] runs.
 */
object DevSettings {
    private const val PREFS = "dynamic_dev_settings"
    private const val KEY_ENVIRONMENT_ID = "environmentId"
    private const val KEY_API_BASE_URL = "apiBaseUrl"

    fun environmentId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENVIRONMENT_ID, null)?.takeIf { it.isNotBlank() }

    fun apiBaseUrl(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API_BASE_URL, null)?.takeIf { it.isNotBlank() }

    fun save(context: Context, environmentId: String?, apiBaseUrl: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENVIRONMENT_ID, environmentId)
            .putString(KEY_API_BASE_URL, apiBaseUrl)
            .apply()
    }
}

object Sdk {
    /**
     * Scope for flows that OUTLIVE the screen that started them.
     *
     * `rememberCoroutineScope()` is cancelled the moment its composable
     * leaves the composition, and every flow that hands the user to another
     * app outlives that: connecting a wallet dismisses the picker dialog
     * (which leaves the composition) and then waits for an answer that
     * arrives seconds later over the WalletConnect relay. The await dies with
     * the scope, the answer lands on a cancelled continuation, and the app
     * simply loses the signature — the failure reads as
     * `LeftCompositionCancellationException: The coroutine scope left the
     * composition`, which names the mechanism but not the cause.
     *
     * Measured with Zerion on a Pixel 8a: connect and sign both reached the
     * wallet, the user signed, and the response was dropped on the way back.
     * Social login has the same shape — a Chrome Custom Tab also leaves the
     * app.
     *
     * Process-lifetime on purpose. A demo does not need cancellation here,
     * and the alternative (the activity's lifecycleScope) still dies if the
     * activity is destroyed while the user is in the wallet.
     */
    val scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )

    private const val DEFAULT_ENVIRONMENT_ID = "3e219b76-dcf1-40ab-aad6-652c4dfab4cc"
    private const val DEFAULT_API_BASE_URL = "https://app.dynamicauth.com/api/v0"

    // EVM networks the demo supports. The first (Sepolia) is the initial active
    // network; switch at runtime via client.wallets.setActiveEvmChainId(chainId).
    private val EVM_NETWORKS = listOf(
        EvmNetwork(11155111L, "https://ethereum-sepolia-rpc.publicnode.com", "Sepolia"),
        EvmNetwork(84532L, "https://sepolia.base.org", "Base Sepolia"),
        EvmNetwork(1L, "https://ethereum-rpc.publicnode.com", "Ethereum"),
        EvmNetwork(137L, "https://polygon-bor-rpc.publicnode.com", "Polygon"),
    )

    // Solana networks the demo supports. The first (devnet) is the initial
    // active network; switch at runtime via client.wallets.setActiveSolanaNetwork(name).
    private val SOLANA_NETWORKS = listOf(
        SolanaNetwork("https://api.devnet.solana.com", "Devnet", "devnet", "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"),
        SolanaNetwork("https://api.mainnet-beta.solana.com", "Mainnet Beta", "mainnet-beta", "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"),
    )

    // BtcClient.sendBitcoin now exists (P2WPKH-only) — this is no longer
    // read-only, unlike when this comment was first written.
    private val BTC_NETWORKS = listOf(
        BtcNetwork("Bitcoin", "https://blockstream.info/api", isTestnet = false),
    )

    // Sui networks the demo supports. The first (devnet) is the initial
    // active network; switch at runtime via client.wallets.setActiveSuiNetwork(name).
    private val SUI_NETWORKS = listOf(
        SuiNetwork("https://graphql.devnet.sui.io/graphql", "Devnet", "devnet"),
        SuiNetwork("https://graphql.testnet.sui.io/graphql", "Testnet", "testnet"),
    )

    private val STELLAR_NETWORKS = listOf(
        StellarNetwork(
            "Test SDF Network ; September 2015",
            "https://horizon-testnet.stellar.org",
            "Testnet",
            "2",
        ),
    )

    private val TON_NETWORKS = listOf(
        TonNetwork("https://testnet.toncenter.com/api/v2/jsonRPC", "Testnet", -3),
    )

    lateinit var client: DynamicClient
        private set
    private var built = false
    lateinit var captcha: DynamicCaptchaWidget
        private set

    /**
     * [context] is used only to read Developer Settings overrides (see
     * [DevSettings]) before building — never stored beyond this call.
     */
    fun build(context: Context, activityProvider: () -> Activity?) {
        if (built) return
        client = DynamicSdk.createDynamicClient(
            context = context,
            activityProvider = activityProvider,
            environmentId = DevSettings.environmentId(context) ?: DEFAULT_ENVIRONMENT_ID,
            apiBaseUrl = DevSettings.apiBaseUrl(context) ?: DEFAULT_API_BASE_URL,
            appName = "dynamic-android-demo",
            universalLink = "https://demo.dynamic.xyz",
            nativeLink = "dynamicdemo://",
        )
        // Opt-in embedded wallets (WaaS), then the chain extensions over it.
        client.useWaas(context, activityProvider)
        // Opt-in private-key export (native WebView engine, independent of useWaas()).
        client.useExportPrivateKey(context, activityProvider)
        // Opt-in external wallets (MetaMask, ...) over native WalletConnect.
        // No projectId or app identity here: the SDK reads the projectId from
        // the environment's settings and the rest from the client config,
        // then hands them to the provider through initialize(). Solana chains
        // come from the SAME list useSolana gets, so an external wallet is
        // asked to sign on exactly the clusters this app supports.
        //
        // Phantom and Solflare use encrypted deep links through this provider.
        externalWallets = ReownExternalWalletProvider(
            context,
            solanaChains = SOLANA_NETWORKS.map { "solana:${it.genesisHash}" },
            mechanisms = listOf(PhantomMechanism(context), SolflareMechanism(context)),
        )
        client.addExtension(ExternalWalletExtension(externalWallets!!))
        client.useEvm(EVM_NETWORKS)
        client.useEarn()
        client.useSolana(SOLANA_NETWORKS)
        client.useBtc(BTC_NETWORKS)
        client.useSui(SUI_NETWORKS)
        client.useStellar(STELLAR_NETWORKS)
        client.useTon(TON_NETWORKS)
        client.useZerodev()
        client.useLegacyWalletUpgrade(activityProvider)
        // Opt-in Business Accounts — no native engine, plain REST calls over WaaS.
        client.useBusinessAccount()
        captcha = DynamicSdk.createCaptchaWidget(activityProvider)
        built = true
    }

    val waas: WaasClient get() = client.waas

    val exportPrivateKey: ExportPrivateKeyClient get() = client.exportPrivateKey

    val evm: EvmExtension get() = client.evm

    val solana: SolanaExtension get() = client.solana

    val btc: BtcClient get() = client.btc

    val sui: SuiClient get() = client.sui

    val businessAccount: BusinessAccountClient get() = client.businessAccount

    /**
     * A wallet's deep-link answer comes back here, and only the host can
     * deliver it: this Activity owns the dynamicdemo:// scheme (see the
     * manifest). Offered to the external-wallet mechanisms first — each
     * consumes only its own redirects — then handed to the SDK's own OAuth
     * handling.
     */
    private var externalWallets: ReownExternalWalletProvider? = null

    fun onNewIntent(intent: Intent) {
        val uri = intent.data?.toString()
        if (uri != null && externalWallets?.handleRedirect(uri) == true) {
            android.util.Log.i("DynamicDemo", "external wallet redirect consumed")
            return
        }
        if (uri != null) {
            // NAMES only, never values: a wallet's answer carries an
            // encrypted payload, and a demo log is not the place for it even
            // encrypted. The names are what tells you WHY a redirect went
            // unclaimed — a wallet that answers with parameters the mechanism
            // does not expect looks exactly like a wallet that never answered.
            val names = android.net.Uri.parse(uri).queryParameterNames.sorted()
            android.util.Log.i(
                "DynamicDemo",
                "redirect NOT claimed by any wallet: scheme=${android.net.Uri.parse(uri).scheme} " +
                    "params=$names",
            )
        }
        DynamicSdk.onNewIntent(intent)
    }
}
