using Dynamic.Sdk;
using Newtonsoft.Json;

namespace DynamicMauiDemo;

/// <summary>Raw user JSON (verifiedCredentials included) + the full auth JWT
/// — mirror of flutter-sdk/example's ProfileScreen "_valueCard" cards. That
/// reference app shows these as the landing screen itself; here it's its
/// own page reached from Home, since this demo's Home is wallet-focused
/// already.
///
/// NOTE: the reference also shows a separate "Min Auth Token" — a genuinely
/// distinct, shorter token pushed from the SDK's own webview/native bridge
/// (auth_module.dart's minifiedTokenChanged store), not a display-side
/// truncation of the full JWT. This generated SDK has no equivalent state
/// field/bridge message yet, so that card isn't reproduced here — it would
/// be new SDK surface (spec + all 4 native bridges), not a UI-only gap. Our
/// own AuthToken is already the JS SDK's PREFERRED minifiedJwt (with a
/// fallback to the legacy jwt) per auth.flows.ts — there's no separate
/// "full" token being hidden here.</summary>
public class ProfilePage : ContentPage
{
    public ProfilePage(DynamicClient client, SdkUser user)
    {
        Title = "Profile";
        var userJson = JsonConvert.SerializeObject(user, Formatting.Indented);
        var token = client.Auth.AuthToken ?? string.Empty;

        var stepUp = new Button { Text = "Step-Up Auth" };
        stepUp.Clicked += async (_, _) => await Navigation.PushAsync(new StepUpPage(client));

        var passkeys = new Button { Text = "Passkeys" };
        passkeys.Clicked += async (_, _) => await Navigation.PushAsync(new PasskeysPage(client));

        var mfaRecoveryCodes = new Button { Text = "MFA recovery codes" };
        mfaRecoveryCodes.Clicked += async (_, _) => await Navigation.PushAsync(new MfaRecoveryCodesPage(client));

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children = { stepUp, passkeys, mfaRecoveryCodes, ValueCard("User:", userJson), ValueCard("Token:", token) },
            },
        };
    }

    private static View ValueCard(string title, string value)
    {
        var text = new Label { Text = value, LineBreakMode = LineBreakMode.CharacterWrap };
        var copy = new Button { Text = "Copy" };
        copy.Clicked += async (_, _) =>
        {
            await Clipboard.SetTextAsync(value);
            copy.Text = "Copied!";
            await Task.Delay(1000);
            copy.Text = "Copy";
        };
        return new Border
        {
            Padding = 12,
            StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
            Content = new VerticalStackLayout
            {
                Spacing = 8,
                Children =
                {
                    new Label { Text = title, FontAttributes = FontAttributes.Bold },
                    new ScrollView { HeightRequest = 300, Content = text },
                    copy,
                },
            },
        };
    }
}
