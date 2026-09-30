using System.Linq;
using Dynamic.Sdk;
using Dynamic.Sdk.Maui;
using Dynamic.Sdk.Evm;
using Dynamic.Sdk.Solana;
using Dynamic.Sdk.Btc;
using Dynamic.Sdk.Sui;
using Dynamic.Sdk.Stellar;
using Dynamic.Sdk.Ton;

namespace DynamicMauiDemo;

public class App : Application
{
    private DynamicClient _client;
    public static MauiCaptchaWidget Captcha { get; } = new();

    /// <summary>
    /// A wallet's deep-link answer comes back to the app itself, and only the
    /// host can deliver it: the platform callbacks that receive it
    /// (MainActivity.OnNewIntent, AppDelegate.OpenUrl) belong to the app, not
    /// to the SDK. Held here so both of them can reach the SAME instances
    /// UseExternalWallet was given.
    ///
    /// Phantom and Solflare speak the same encrypted deep-link protocol — see
    /// SolanaDeepLinkWallet for the five values that differ. Coinbase is a
    /// mechanism too, for a different reason: it has a real popup WebView of
    /// its own rather than a deep link, so its HandleRedirect is a no-op —
    /// it is still offered every incoming link, and simply never claims one.
    /// </summary>
    public static readonly IExternalWalletMechanism[] ExternalWalletMechanisms =
    {
        new PhantomMechanism(url => Launcher.OpenAsync(url)),
        new SolflareMechanism(url => Launcher.OpenAsync(url)),
        new CoinbaseWebviewMechanism(),
    };

    /// <summary>
    /// Offers an incoming link to each deep-link wallet, stopping at the
    /// first that claims it. A link nobody claims is somebody else's — a
    /// social-login callback arrives on the very same scheme.
    /// </summary>
    public static bool HandleWalletRedirect(string uri)
    {
        foreach (var mechanism in ExternalWalletMechanisms)
        {
            if (mechanism.HandleRedirect(uri)) return true;
        }
        return false;
    }

    public App()
    {
        // Light, always. DESIGN.md: "No dark mode. The reference forces light
        // theme only ... Force light appearance on every platform." MAUI was
        // the one platform that did not: on a phone in dark mode it rendered
        // system dark defaults over a palette built for light, which looks
        // broken rather than themed. The iOS demo forces it with
        // .preferredColorScheme(.light), Compose builds only a
        // lightColorScheme, and Flutter's MaterialApp sets no darkTheme — so
        // this line is what brings MAUI in line rather than a new decision.
        UserAppTheme = AppTheme.Light;

        // Global implicit styling (AppStyles' peer of theme.dart) — applies
        // to every page/button/label/etc. across the app with no per-page
        // changes, mirroring MaterialApp(theme:) in the Flutter demo.
        foreach (var style in AppStyles.All())
        {
            Resources.Add(style);
        }

        _client = BuildClient();
        SubscribeToWalletPrompts(_client);
    }

    // Rebuild with current settings. Each extension remains opt-in.
    private static DynamicClient BuildClient() =>
        DynamicSdk.CreateDynamicClient(new DynamicConfig(
                environmentId: DevSettings.EnvironmentId,
                apiBaseUrl: DevSettings.ApiBaseUrl,
                appName: "dynamic-maui-demo",
                nativeLink: DemoConfig.NativeLink,
                universalLink: DemoConfig.UniversalLink))
                // Opt-in embedded wallets (WaaS, native WebView engine).
                .UseWaas()
                // Opt-in private-key export (native WebView engine, independent of UseWaas()).
                .UseExportPrivateKey()
                // Opt-in external wallets (MetaMask, ...) over native
                // WalletConnect. No projectId or app identity here: the SDK
                // reads the projectId from the environment's settings and the
                // rest from the config above, then hands them to the provider
                // through Initialize(). Solana chains come from the SAME
                // network list UseSolana gets, so an external wallet is asked
                // to sign on exactly the clusters this app supports.
                //
                // Phantom, Solflare and Coinbase are added as MECHANISMS
                // rather than a second provider: the provider's own
                // WalletConnect namespace above cannot reach any of them.
                // Phantom/Solflare share an encrypted deep-link protocol;
                // Coinbase's connect() is a real popup window instead. Each
                // is its own package because Phantom brings a NaCl
                // implementation and Coinbase brings the shared native
                // interop bridge — see Dynamic.Sdk.ExternalWallet.Phantom /
                // Dynamic.Sdk.MAUI.ExternalWallet.Coinbase.
                .UseExternalWallet(
                    solanaChains: DemoConfig.SolanaNetworks
                        .Select(network => $"solana:{network.GenesisHash}")
                        .ToArray(),
                    mechanisms: ExternalWalletMechanisms)
                // Opt-in multi-operator wallet containers (no native engine — pure API/state).
                .UseBusinessAccount()
                .UseEarn()
                // Opt-in EVM chain extension (Nethereum, signs via WaaS).
                .UseEvm(DemoConfig.EvmNetworks)
                // Opt-in Solana chain extension (Solnet, signs via WaaS).
                .UseSolana(DemoConfig.SolanaNetworks)
                .UseStellar(DemoConfig.StellarNetworks)
                .UseZerodev()
            .UseBtc(DemoConfig.BtcNetworks)
            .UseSui(DemoConfig.SuiNetworks)
            .UseTon(DemoConfig.TonNetworks)
            .UseLegacyWalletUpgrade()
            .Build();

    /// <summary>
    /// "Go to your wallet." Over WalletConnect a signature request travels
    /// down an already-open relay session with no deep link of its own, so
    /// without this prompt the app just looks frozen while the wallet waits
    /// off-screen. Subscribed once here, because every screen that signs with
    /// an external wallet needs it.
    /// </summary>
    private static void SubscribeToWalletPrompts(DynamicClient client)
    {
        client.ExternalWalletActionRequested += request =>
            MainThread.BeginInvokeOnMainThread(() =>
            {
                var page = Current?.Windows.FirstOrDefault()?.Page;
                if (page == null) return;
                var what = request.Action == "signTransaction" ? "transaction" : "signature";
                var wallet = string.IsNullOrEmpty(request.WalletName) ? "your wallet" : request.WalletName;
                // Fire and forget: this is a notification, and the call it
                // announces is already in flight.
                _ = page.DisplayAlertAsync("Approve in your wallet", $"Open {wallet} to approve the {what}.", "OK");
            });
    }

    protected override Window CreateWindow(IActivationState? activationState) =>
        new Window(AppStyles.Wrap(new SplashPage(_client)));

    /// <summary>Swaps the app's root page (login ⇄ home). Navigation only.</summary>
    public static void SetRoot(Page page)
    {
        var window = Current!.Windows[0];
        window.Page = page;
    }

    /// <summary>Rebuilds the client and splash page with the current developer settings.</summary>
    public static void RestartWithCurrentSettings()
    {
        var app = (App)Current!;
        app._client = BuildClient();
        SetRoot(AppStyles.Wrap(new SplashPage(app._client)));
    }
}
