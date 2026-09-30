using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.OS;
using Dynamic.Sdk.Maui;

namespace DynamicMauiDemo;

// LaunchMode.SingleTask, not SingleTop, and the difference is load-bearing
// for external wallets. SingleTop reuses the running instance only when it is
// already on top of the TARGET task — and a wallet that opens dynamicdemo://
// without FLAG_ACTIVITY_NEW_TASK puts this activity into ITS task, where no
// instance of ours exists. Android then builds a SECOND instance there,
// OnNewIntent never fires on the one holding the pending connect, and the
// answer is lost: the app just comes to the foreground with nothing happening.
// It also shows our UI inside the wallet's own recents card.
//
// Measured with Solflare on a Pixel 8a (the Kotlin demo's manifest carries the
// same note). Phantom happened to work because it adds NEW_TASK itself, which
// is not something a dApp can rely on.
[Activity(Theme = "@style/Maui.SplashTheme", MainLauncher = true, LaunchMode = LaunchMode.SingleTask,
    ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation | ConfigChanges.UiMode |
        ConfigChanges.ScreenLayout | ConfigChanges.SmallestScreenSize | ConfigChanges.Density)]
// OAuth redirect deeplink — mirrors the Flutter demo's dynamicdemo:// scheme.
[IntentFilter(new[] { Intent.ActionView },
    Categories = new[] { Intent.CategoryDefault, Intent.CategoryBrowsable },
    DataScheme = "dynamicdemo")]
public class MainActivity : MauiAppCompatActivity
{
    /// <summary>
    /// A wallet's answer can arrive as the LAUNCH intent, not only through
    /// OnNewIntent: Android delivers it here when it has destroyed this
    /// activity but kept the process, in which case the mechanism still holds
    /// the pending connect and its keypair. Without this the link is dropped
    /// silently. Peer of the Kotlin demo's MainActivity.onCreate.
    /// </summary>
    protected override void OnCreate(Bundle? savedInstanceState)
    {
        base.OnCreate(savedInstanceState);
        // Only when it carries data: a plain launcher intent has none.
        if (Intent?.Data != null) OnNewIntent(Intent);
    }

    protected override void OnNewIntent(Intent? intent)
    {
        base.OnNewIntent(intent);
        if (intent is null) return;
        // The deep-link wallets first: each consumes only its own redirects, and offering the
        // link to the OAuth browser afterwards would let a wallet answer
        // complete an unrelated pending sign-in.
        var uri = intent.Data?.ToString();
        if (uri is not null && App.HandleWalletRedirect(uri)) return;
        // Forward the OAuth callback into the shared native browser.
        MauiOAuthBrowser.Current?.OnNewIntent(intent);
    }
}
