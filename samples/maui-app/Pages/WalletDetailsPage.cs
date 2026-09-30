using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Wallet detail view — including, for every Tier-1 chain
/// (EVM/SOL/BTC/SUI), balance (current network + all configured networks,
/// where the chain has more than one) and, for EVM/SOL/SUI, the network
/// switcher (BTC has no headless active-network state — see BtcClient's
/// doc). The network switcher used to live on Home, one picker for the
/// whole app; it's scoped here instead, next to the wallet it actually
/// affects.
///
/// Per-chain actions section (CAPABILITIES.md's Sui/BTC coverage item):
/// every wallet gets Sign message + Send; EVM additionally gets Sign typed
/// data; BTC additionally gets Sign PSBT; Sui additionally gets Sign
/// transaction. All reuse SignMessagePage/SendPage's own chain-branching
/// rather than new screens per chain.
///
/// NOTE this doesn't make network selection per-wallet under the hood —
/// SetActiveEvmChainId/SetActiveSolanaNetwork/SetActiveSuiNetwork are still
/// one client-wide value each (see WalletsClient/state.ts), so switching it
/// from ANY wallet of that chain's Details page changes it for every other
/// wallet of that chain too. That's a real architectural gap vs. the
/// "picture" of independent per-wallet networks, not something this UI
/// move fixes — flagged via the caption under the picker rather than
/// silently implying otherwise.</summary>
public class WalletDetailsPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly Wallet _wallet;
    private readonly Label _balanceLabel = new() { Text = "Balance: loading…" };
    private readonly VerticalStackLayout _multichainBalances = new() { Spacing = 2 };

    public WalletDetailsPage(DynamicClient client, Wallet wallet)
    {
        _client = client;
        _wallet = wallet;
        Title = "Wallet";

        var copy = new Button { Text = "Copy address" };
        copy.Clicked += async (_, _) => await Clipboard.SetTextAsync(wallet.Address);

        var sign = new Button { Text = "Sign message" };
        sign.Clicked += async (_, _) => await Navigation.PushAsync(new SignMessagePage(client, wallet));

        var password = new Button { Text = "Wallet password" };
        password.Clicked += async (_, _) => await Navigation.PushAsync(new WalletPasswordPage(client, wallet));

        var export = new Button { Text = "Export private key" };
        export.Clicked += async (_, _) => await Navigation.PushAsync(new ExportPrivateKeyPage(client, wallet));

        var children = new List<View>
        {
            new Label { Text = "Chain", FontAttributes = FontAttributes.Bold },
            new Label { Text = wallet.Chain },
            new Label { Text = "Address", FontAttributes = FontAttributes.Bold },
            new Label { Text = wallet.Address, LineBreakMode = LineBreakMode.CharacterWrap },
            new Label { Text = "Type", FontAttributes = FontAttributes.Bold },
            new Label { Text = wallet.IsEmbedded ? "Embedded (WaaS)" : "External wallet" },
            _balanceLabel,
        };

        switch (wallet.Chain)
        {
            case "EVM":
                children.Add(BuildEvmNetworkPicker(client));
                break;
            case "SOL":
                children.Add(BuildSolanaNetworkPicker(client));
                break;
            case "SUI":
                children.Add(BuildSuiNetworkPicker(client));
                break;
            // BTC: no headless active-network switch — see BtcClient's doc.
        }

        if (wallet.Chain != "BTC")
        {
            // No GetMultichainBalance primitive exists in the SDK — this loops
            // AvailableNetworks() and calls the existing per-call
            // GetBalance(address, network) once per network, concurrently via
            // Task.WhenAll, catching failures individually so one dead RPC
            // doesn't blank the whole list.
            children.Add(new Label { Text = "Balances on all networks", FontAttributes = FontAttributes.Bold });
            children.Add(_multichainBalances);
        }

        children.Add(copy);
        children.Add(sign);

        // Per-chain extra actions (CAPABILITIES.md's Sui/BTC coverage item).
        switch (wallet.Chain)
        {
            case "EVM":
                var signTyped = new Button { Text = "Sign typed data" };
                signTyped.Clicked += async (_, _) => await Navigation.PushAsync(new SignTypedDataPage(client, wallet));
                children.Add(signTyped);
                break;
            case "BTC":
                var signPsbt = new Button { Text = "Sign PSBT" };
                signPsbt.Clicked += async (_, _) => await Navigation.PushAsync(new SignMessagePage(client, wallet, SignKind.RawTransaction));
                children.Add(signPsbt);
                break;
            case "SUI":
                var signTx = new Button { Text = "Sign transaction" };
                signTx.Clicked += async (_, _) => await Navigation.PushAsync(new SignMessagePage(client, wallet, SignKind.RawTransaction));
                children.Add(signTx);
                break;
        }

        // Every Tier-1 chain has a chain extension installed (see App.cs),
        // so Send is always offered — was only reachable from the Home
        // wallet card before.
        var send = new Button { Text = wallet.Chain == "BTC" ? "Send bitcoin" : "Send" };
        send.Clicked += async (_, _) => await Navigation.PushAsync(new SendPage(client, wallet));
        children.Add(send);

        children.Add(password);
        children.Add(export);

        var layout = new VerticalStackLayout { Padding = 16, Spacing = 16 };
        foreach (var child in children) layout.Children.Add(child);
        Content = new ScrollView { Content = layout };
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        try
        {
            _balanceLabel.Text = $"Balance: {await ChainUnits.FormatBalanceAsync(_client, _wallet)}";
        }
        catch (Exception ex)
        {
            _balanceLabel.Text = $"Balance: unavailable ({ex.Message})";
        }

        _multichainBalances.Children.Clear();
        switch (_wallet.Chain)
        {
            case "SOL":
                await LoadMultichainAsync(_client.Solana().AvailableNetworks(),
                    network => _client.Solana().GetBalanceAsync(_wallet.Address, network));
                break;
            case "SUI":
                await LoadMultichainAsync(_client.Sui().AvailableNetworks(),
                    network => _client.Sui().GetBalanceAsync(_wallet.Address, network: network));
                break;
            case "EVM":
                await LoadMultichainAsync(_client.Evm().AvailableNetworks(),
                    async network => (await _client.Evm().GetBalanceAsync(_wallet.Address, network)).ToString());
                break;
            // BTC: single configured network, already shown via _balanceLabel.
        }
    }

    private async Task LoadMultichainAsync<TNetwork>(IReadOnlyList<TNetwork> networks, Func<TNetwork, Task<string>> getBalanceUnits)
        where TNetwork : notnull
    {
        var results = await Task.WhenAll(networks.Select(async network =>
        {
            var name = NetworkName(network);
            try
            {
                var units = System.Numerics.BigInteger.Parse(await getBalanceUnits(network));
                return (name, $"{ChainUnits.UnitsToToken(_wallet.Chain, units)} {ChainUnits.UnitName(_wallet.Chain)}");
            }
            catch (Exception)
            {
                return (name, "unavailable");
            }
        }));
        foreach (var (name, balance) in results) _multichainBalances.Children.Add(new Label { Text = $"{name}: {balance}" });
    }

    private static string NetworkName<TNetwork>(TNetwork network) => network switch
    {
        EvmNetwork evm => evm.Name,
        SolanaNetwork sol => sol.Name,
        SuiNetwork sui => sui.Name,
        _ => network!.ToString() ?? "?",
    };

    private static View BuildEvmNetworkPicker(DynamicClient client)
    {
        var evm = client.GetExtension<EvmExtension>()!;
        var networks = evm.AvailableNetworks();
        var picker = new Picker { Title = "EVM network", ItemsSource = networks.Select(n => n.Name).ToList() };
        var current = client.Wallets.ActiveEvmChainId;
        var selected = current is null ? -1 : networks.ToList().FindIndex(n => n.ChainId == current);
        picker.SelectedIndex = selected >= 0 ? selected : 0;
        picker.SelectedIndexChanged += (_, _) =>
        {
            if (picker.SelectedIndex < 0) return;
            client.Wallets.SetActiveEvmChainId(networks[picker.SelectedIndex].ChainId);
        };
        return new VerticalStackLayout
        {
            Spacing = 4,
            Children =
            {
                new Label { Text = "Network", FontAttributes = FontAttributes.Bold },
                picker,
                new Label { Text = "Applies to all EVM wallets on this client.", Style = AppStyles.CaptionLabel },
            },
        };
    }

    private static View BuildSolanaNetworkPicker(DynamicClient client)
    {
        var solana = client.GetExtension<SolanaExtension>()!;
        var networks = solana.AvailableNetworks();
        var picker = new Picker { Title = "Solana network", ItemsSource = networks.Select(n => n.Name).ToList() };
        var current = client.Wallets.ActiveSolanaNetwork;
        var selected = current is null ? -1 : networks.ToList().FindIndex(n => n.Name == current);
        picker.SelectedIndex = selected >= 0 ? selected : 0;
        picker.SelectedIndexChanged += (_, _) =>
        {
            if (picker.SelectedIndex < 0) return;
            client.Wallets.SetActiveSolanaNetwork(networks[picker.SelectedIndex].Name);
        };
        return new VerticalStackLayout
        {
            Spacing = 4,
            Children =
            {
                new Label { Text = "Network", FontAttributes = FontAttributes.Bold },
                picker,
                new Label { Text = "Applies to all Solana wallets on this client.", Style = AppStyles.CaptionLabel },
            },
        };
    }

    private static View BuildSuiNetworkPicker(DynamicClient client)
    {
        var sui = client.Sui();
        var networks = sui.AvailableNetworks();
        var picker = new Picker { Title = "Sui network", ItemsSource = networks.Select(n => n.Name).ToList() };
        var current = client.Wallets.ActiveSuiNetwork;
        var selected = current is null ? -1 : networks.ToList().FindIndex(n => n.Name == current);
        picker.SelectedIndex = selected >= 0 ? selected : 0;
        picker.SelectedIndexChanged += (_, _) =>
        {
            if (picker.SelectedIndex < 0) return;
            client.Wallets.SetActiveSuiNetwork(networks[picker.SelectedIndex].Name);
        };
        return new VerticalStackLayout
        {
            Spacing = 4,
            Children =
            {
                new Label { Text = "Network", FontAttributes = FontAttributes.Bold },
                picker,
                new Label { Text = "Applies to all Sui wallets on this client.", Style = AppStyles.CaptionLabel },
            },
        };
    }
}
