using Foundation;
using UIKit;

namespace DynamicMauiDemo;

[Register("AppDelegate")]
public class AppDelegate : MauiUIApplicationDelegate
{
    protected override MauiApp CreateMauiApp() => MauiProgram.CreateMauiApp();

    /// <summary>
    /// Where a deep-link wallet's answer arrives on iOS — the peer of
    /// MainActivity.OnNewIntent. The wallets are offered the link first, and
    /// consumes only its own; everything else goes on to MAUI as before.
    /// </summary>
    public override bool OpenUrl(UIApplication app, NSUrl url, NSDictionary options)
    {
        var uri = url.AbsoluteString;
        if (uri is not null && App.HandleWalletRedirect(uri)) return true;
        return base.OpenUrl(app, url, options);
    }
}
