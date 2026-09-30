using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Wallet list + create + set-primary + sign-out, plus per-wallet
/// send (see SendPage). EVM/Solana network switching lives in WalletDetails
/// now, scoped to the wallet you tapped in, not floating here for the whole
/// app.</summary>
public class HomePage : ContentPage
{
    private readonly DynamicClient _client;
    private SdkUser? _user;
    private string? _primaryId;
    private readonly VerticalStackLayout _walletList = new() { Spacing = 12 };
    // getBalance(address) accepts any address, not just the caller's own —
    // cached by address so RefreshList (called on every UserChanged/
    // WalletCreated/PrimaryChanged event, not just once) doesn't re-fire the
    // RPC for wallets it's already fetched.
    private readonly Dictionary<string, string> _balanceCache = new();

    public HomePage(DynamicClient client, SdkUser user)
    {
        _client = client;
        _user = user;
        Title = "Wallets";

        // Create Wallet is its own screen now (was inline here) — see
        // CreateWalletPage, which also offers the password-protect switch
        // and uses the pill/filled button style for its submit CTA.
        var create = new Button { Text = "Create wallet" };
        create.Clicked += async (_, _) => await Navigation.PushAsync(new CreateWalletPage(_client));

        var importKey = new Button { Text = "Import private key" };
        importKey.Clicked += async (_, _) => await Navigation.PushAsync(new ImportPrivateKeyPage(_client));

        var businessAccounts = new Button { Text = "Business accounts" };
        businessAccounts.Clicked += async (_, _) => await Navigation.PushAsync(new BusinessAccountsListPage(_client));

        var profile = new Button { Text = "Profile" };
        profile.Clicked += async (_, _) => await Navigation.PushAsync(new ProfilePage(_client, _user!));

        var signOut = new Button { Text = "Sign out" };
        signOut.Clicked += OnSignOut;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children =
                {
                    new Label { Text = "Embedded wallets", Style = AppStyles.SectionHeading },
                    _walletList,
                    create, importKey, businessAccounts, profile, signOut,
                },
            },
        };

        _client.UserChanged += OnUserChanged;
        _client.WalletCreated += OnWalletCreated;
        _client.PrimaryWalletIdChanged += OnPrimaryChanged;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        _primaryId = _client.Wallets.PrimaryWalletId;
        RefreshList();
    }

    private void OnUserChanged(SdkUser? user) =>
        MainThread.BeginInvokeOnMainThread(() => { _user = user; RefreshList(); });

    private void OnWalletCreated(WaasCreatedWallet wallet) =>
        MainThread.BeginInvokeOnMainThread(RefreshList);

    private void OnPrimaryChanged(string? id) =>
        MainThread.BeginInvokeOnMainThread(() => { _primaryId = id; RefreshList(); });

    private void RefreshList()
    {
        _walletList.Children.Clear();
        // The generated Wallet model, not a hand-rolled row type — each entry
        // already carries SignMessage()/ExportPrivateKey() bound to this client.
        var wallets = _client.Wallets.UserWallets;
        if (wallets.Count == 0)
        {
            _walletList.Children.Add(new Label { Text = "No wallets yet." });
            return;
        }
        foreach (var wallet in wallets) _walletList.Children.Add(BuildCard(wallet));
    }

    private View BuildCard(Wallet wallet)
    {
        var isPrimary = wallet.Id == _primaryId;
        var title = new Label
        {
            Text = $"{(isPrimary ? "★ " : string.Empty)}{wallet.Chain} · {Shorten(wallet.Address)}",
            Style = AppStyles.ListItemTitle,
        };

        var sign = new Button { Text = "Sign" };
        sign.Clicked += async (_, _) => await Navigation.PushAsync(new SignMessagePage(_client, wallet));

        var primary = new Button { Text = isPrimary ? "Primary" : "Set primary", IsEnabled = !isPrimary };
        primary.Clicked += async (_, _) =>
        {
            try { await _client.Wallets.SetPrimaryWalletAsync(wallet.Id); }
            catch (DynamicException ex) { await this.DisplayAlertAsync("Error", ex.Message, "OK"); }
        };

        var copy = new Button { Text = "Copy" };
        copy.Clicked += async (_, _) => await Clipboard.SetTextAsync(wallet.Address);

        var actions = new HorizontalStackLayout { Spacing = 8, Children = { sign, primary, copy } };

        // Every Tier-1 chain (EVM/SOL/BTC/SUI) has a chain extension
        // installed (see App.cs), so every wallet gets a balance + Send.
        var send = new Button { Text = "Send" };
        send.Clicked += async (_, _) => await Navigation.PushAsync(new SendPage(_client, wallet));
        actions.Children.Add(send);

        // Not hardcoded: UserWallets lists EXTERNAL wallets too, and each
        // Wallet says which extension signs for it.
        var walletType = wallet.IsEmbedded ? "Type: Embedded (WaaS)" : "Type: External wallet";
        var cardChildren = new List<View> { title, new Label { Text = walletType } };
        var balanceLabel = new Label { Text = _balanceCache.TryGetValue(wallet.Address, out var cached) ? cached : "Balance: loading…" };
        cardChildren.Add(balanceLabel);
        if (!_balanceCache.ContainsKey(wallet.Address)) _ = LoadBalanceAsync(wallet, balanceLabel);
        cardChildren.Add(actions);

        var cardLayout = new VerticalStackLayout { Spacing = 8 };
        foreach (var child in cardChildren) cardLayout.Children.Add(child);

        var border = new Border
        {
            // Stroke/BackgroundColor come from AppStyles' implicit Border style.
            Padding = 12,
            StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
            Content = cardLayout,
        };
        // Tap the card itself to open Details — no separate "Details" button,
        // matching examples/flutter-app's WalletCard.onPressed (the reference
        // this demo is styled after). The action buttons stay for the
        // operations that AREN'T "go look at this wallet".
        var tap = new TapGestureRecognizer();
        tap.Tapped += async (_, _) => await Navigation.PushAsync(new WalletDetailsPage(_client, wallet));
        border.GestureRecognizers.Add(tap);
        return border;
    }

    private static string Shorten(string address) =>
        address.Length <= 12 ? address : $"{address[..6]}…{address[^4..]}";

    private async Task LoadBalanceAsync(Wallet wallet, Label label)
    {
        string text;
        try
        {
            text = $"Balance: {await ChainUnits.FormatBalanceAsync(_client, wallet)}";
        }
        catch (Exception)
        {
            text = "Balance: unavailable";
        }
        _balanceCache[wallet.Address] = text;
        MainThread.BeginInvokeOnMainThread(() => label.Text = text);
    }

    private async void OnSignOut(object? sender, EventArgs e)
    {
        try { await _client.Auth.SignOutAsync(); }
        catch { /* best-effort */ }
        _client.UserChanged -= OnUserChanged;
        _client.WalletCreated -= OnWalletCreated;
        _client.PrimaryWalletIdChanged -= OnPrimaryChanged;
        App.SetRoot(AppStyles.Wrap(new LoginPage(_client)));
    }
}
