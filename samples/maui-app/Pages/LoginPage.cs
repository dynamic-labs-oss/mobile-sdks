using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Email-OTP + social login.</summary>
public class LoginPage : ContentPage
{
    /// <summary>
    /// The wallets this demo offers, so each one can be tested by hand.
    /// Mirrored in the Android, iOS and Flutter demos.
    ///
    /// REGISTRY KEYS, not names, and that matters: a name match picks the
    /// wrong wallet here. "Trust" also matches <c>ta</c> (TrustAssetApp) and
    /// "Bitget" also matches <c>bgw</c>, whose link is a Telegram bot, not
    /// the app.
    ///
    /// Most connect over WalletConnect: each has sign_v2 and a mobile deep
    /// link, so the tap opens that wallet straight away (see
    /// ExternalWalletOption.ConnectionDeeplink).
    ///
    /// Phantom and Solflare have NEITHER, and go through the shared Solana
    /// deep-link mechanism instead, which builds its own URL — see
    /// SolanaDeepLinkWallet for the five values that differ between the two.
    ///
    /// Xverse is missing from this demo only: .NET does not propose bip122
    /// (Bitcoin) yet, unlike Kotlin, Swift and Dart.
    /// Coinbase is missing because the SDK cannot reach it — sign_v1 only, no
    /// deep link.
    /// </summary>
    private static readonly HashSet<string> TestWalletKeys = new()
    {
        "metamask",
        "phantom",
        "solflare",
        "trust",
        "okxwallet",
        "rabby",
        "rainbow",
        "backpack",
        "bitgetwallet",
        "exodus",
        "zerion",
    };

    private readonly DynamicClient _client;
    private readonly Entry _email = new() { Placeholder = "you@example.com", Keyboard = Keyboard.Email };
    private readonly Button _send = new() { Text = "Send code" };
    private readonly ActivityIndicator _busy = new();
    private readonly VerticalStackLayout _social = new() { Spacing = 8 };
    private readonly Entry _externalJwt = new() { Placeholder = "Paste a JWT from your own identity provider" };
    private readonly Button _byoaSend = new() { Text = "Sign in with external JWT" };
    private readonly Button _wallet = new() { Text = "Continue with a wallet" };

    public LoginPage(DynamicClient client)
    {
        _client = client;
        Title = "Sign in";
        _send.Clicked += OnSend;
        _byoaSend.Clicked += OnByoaSend;
        _wallet.Clicked += OnWallet;

        // The real Dynamic wordmark (dynamic_logo.png, pulled from the
        // Dynamic Flutter SDK example app's assets/dynamic-logo.png), same
        // 200x100 sizing the reference uses. Hidden developer tools: 7 quick
        // taps — same gesture the reference's logo uses (SecretTapDetector).
        var logo = new Image
        {
            Source = "dynamic_logo.png",
            WidthRequest = 200,
            HeightRequest = 100,
            HorizontalOptions = LayoutOptions.Center,
        };
        SecretTapDetector.Attach(logo, () => Navigation.PushAsync(new DeveloperSettingsPage()));

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 24,
                Spacing = 16,
                Children =
                {
                    logo,
                    new Label { Text = "Email" }, _email, _send, _busy,
                    new Label { Text = "Or continue with", Margin = new Thickness(0, 16, 0, 0) }, _social,
                    // External wallet (MetaMask, ...) over WalletConnect. The
                    // picker below comes from the SDK's own wallet registry.
                    _wallet,
                    // BYOA (bring-your-own-auth): exchange a JWT the HOST
                    // app's own identity provider issued for a Dynamic
                    // session, instead of Dynamic owning the credential. No
                    // externalUserId field — the JWT's `sub` claim IS the
                    // external user id (SignInWithExternalJwt's own doc).
                    new Label { Text = "External JWT", Margin = new Thickness(0, 16, 0, 0) },
                    _externalJwt,
                    _byoaSend,
                },
            },
        };
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        try
        {
            var providers = _client.Auth.SocialProviders;
            _social.Children.Clear();
            // Provider.ProviderKind is ONE flat list shared by social login,
            // SMS, ramp, and account-abstraction (ZeroDev) providers alike
            // (see the generated Provider model's own doc) — not
            // social-login-specific. Filters to entries actually usable as
            // an OAuth social button: not "zerodev" and carrying a
            // BaseAuthUrl (SignInWithSocial needs it to build the
            // provider's authorization URL; an SMS/ramp entry wouldn't have
            // one populated). A structural heuristic, not a hardcoded
            // provider list — mirrors ios-app's socialProviders filter
            // exactly, so it doesn't drift as new social providers get
            // enabled backend-side.
            foreach (var provider in providers ?? new())
            {
                if (provider.ProviderKind == "zerodev" || provider.BaseAuthUrl is null) continue;
                var button = new Button { Text = provider.ProviderKind };
                if (provider.ProviderKind == "google")
                {
                    button.ImageSource = "google_logo.png";
                }
                button.Clicked += async (_, _) => await OnSocial(provider.ProviderKind);
                _social.Children.Add(button);
            }
        }
        catch
        {
            // Social providers are optional; ignore failures.
        }
    }

    private async void OnSend(object? sender, EventArgs e)
    {
        var email = _email.Text?.Trim();
        if (string.IsNullOrEmpty(email)) return;
        _busy.IsRunning = true;
        _send.IsEnabled = false;
        try
        {
            var result = await _client.Auth.SendEmailOtpAsync(email);
            await Navigation.PushAsync(new OtpPage(_client, result.VerificationUuid, result.Email));
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _send.IsEnabled = true;
        }
    }

    private async Task OnSocial(string provider)
    {
        _busy.IsRunning = true;
        try
        {
            var user = await _client.Auth.SignInWithSocialAsync(provider);
            // No explicit auto-create-wallets call needed — see OtpPage's comment.
            App.SetRoot(AppStyles.Wrap(new HomePage(_client, user)));
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
        }
    }

    /// <summary>
    /// Sign-in with a wallet the user already has: pick one, hand the
    /// returned deep link to the OS, then wait for the wallet to approve.
    /// </summary>
    private async void OnWallet(object? sender, EventArgs e)
    {
        _busy.IsRunning = true;
        _wallet.IsEnabled = false;
        try
        {
            // Empty when the registry cannot be reached, which is not fatal:
            // a pairing works without a picker, the user just gets the OS
            // chooser instead.
            var options = await _client.ExternalWallet().ListExternalWalletsAsync();
            string? walletKey = null;
            if (options.Count > 0)
            {
                // A real picker shows the head of the registry above a search
                // field. This demo shows a FIXED shortlist instead, so every
                // wallet worth testing by hand is one tap away: the registry
                // order buries Exodus at 154 and Zerion at 509, and any
                // "top N" cut hides them.
                var shown = options.Where(option => TestWalletKeys.Contains(option.Key)).ToList();
                var choice = await this.DisplayActionSheetAsync(
                    "Connect a wallet", "Cancel", null,
                    shown.Select(option => option.Name).Append("Other wallet").ToArray());
                if (choice == null || choice == "Cancel") return;
                walletKey = shown.FirstOrDefault(option => option.Name == choice)?.Key;
            }

            var handle = await _client.ExternalWallet().BeginExternalWalletConnectAsync(
                new List<string> { "EVM", "SOL" }, walletKey);
            // A null uri means this mechanism needs nothing presented. On a
            // phone there is always a wallet app to hand it to, so it is
            // opened rather than shown as a QR code.
            if (!string.IsNullOrEmpty(handle.Uri))
            {
                await Launcher.OpenAsync(new Uri(handle.Uri!));
            }
            var accounts = await _client.ExternalWallet().AwaitExternalWalletConnectionAsync();
            if (accounts.Count == 0)
            {
                await this.DisplayAlertAsync("Error", "Wallet approved the connection but exposed no accounts", "OK");
                return;
            }
            var user = await _client.ExternalWallet().SignInWithExternalWalletAsync(accounts[0].Address);
            App.SetRoot(AppStyles.Wrap(new HomePage(_client, user)));
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _wallet.IsEnabled = true;
        }
    }

    private async void OnByoaSend(object? sender, EventArgs e)
    {
        var jwt = _externalJwt.Text?.Trim();
        if (string.IsNullOrEmpty(jwt)) return;
        _busy.IsRunning = true;
        _byoaSend.IsEnabled = false;
        try
        {
            var user = await _client.Auth.SignInWithExternalJwtAsync(jwt);
            App.SetRoot(AppStyles.Wrap(new HomePage(_client, user)));
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _byoaSend.IsEnabled = true;
        }
    }
}
