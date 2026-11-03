using Dynamic.Sdk.Btc;
using Dynamic.Sdk;
using Dynamic.Sdk.Sui;

namespace DynamicMauiDemo;

/// <summary>Native-token transfer for all four Tier-1 chains: build +
/// WaaS-sign + broadcast via client.Evm()/Solana()/Btc()/Sui(), branching
/// on Wallet.Chain — extended from the original EVM/Solana-only version
/// per CAPABILITIES.md's "Sui and BTC actions on Wallet Details" item
/// (reuse this screen's chain-branching rather than building new ones).
/// Amounts are entered in the chain's display unit (ETH/SOL/BTC/SUI).
/// Mirrors send_screen.dart.</summary>
public class SendPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly Wallet _wallet;
    private readonly Entry _to;
    private readonly Entry _amount;
    private readonly Button _send = new() { Text = "Send" };
    private readonly Label _balance = new() { Text = "Balance: …" };
    private readonly Label _result = new() { LineBreakMode = LineBreakMode.CharacterWrap };
    private readonly Label _status = new() { LineBreakMode = LineBreakMode.WordWrap };
    private readonly ActivityIndicator _busy = new();
    private readonly Picker _suiNetwork = new() { Title = "Sui network", IsVisible = false };
    private readonly Button _faucet = new() { Text = "Copy faucet command", IsVisible = false };
    private readonly Label _networkWarning = new() { IsVisible = false };

    private string Chain => _wallet.Chain;
    private string UnitName => ChainUnits.UnitName(Chain);

    public SendPage(DynamicClient client, Wallet wallet)
    {
        _client = client;
        _wallet = wallet;
        Title = "Send";
        _to = new Entry { Placeholder = RecipientPlaceholder(Chain) };
        _amount = new Entry { Placeholder = "0.001", Text = "0.001", Keyboard = Keyboard.Numeric };
        _send.Clicked += OnSend;
        ConfigureSuiNetworkPicker();
        _faucet.Clicked += async (_, _) =>
        {
            await Clipboard.SetTextAsync($"sui client faucet --address {_wallet.Address}");
            await this.DisplayAlertAsync("Sui", "Faucet command copied", "OK");
        };
        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children =
                {
                    new Label { Text = $"From: {_wallet.Address}", LineBreakMode = LineBreakMode.CharacterWrap },
                    _balance,
                    _suiNetwork,
                    new Label
                    {
                        Text = "Sui transfer test\n1. Select the Sui network above.\n2. Fund this address on the same network.\n3. Enter another address on that network and an amount.\n4. Send, approve the signature, and check the digest status.",
                        IsVisible = Chain == "SUI",
                    },
                    _networkWarning,
                    _faucet,
                    new Label { Text = "To" }, _to,
                    new Label { Text = $"Amount ({UnitName})" }, _amount,
                    _send, _busy,
                    new Label { Text = "Result", FontAttributes = FontAttributes.Bold }, _result, _status,
                },
            },
        };
    }

    private static string RecipientPlaceholder(string chain) => chain switch
    {
        "SOL" => "recipient (base58)",
        "BTC" => "recipient (bech32, P2WPKH only)",
        "SUI" => "recipient (0x…)",
        _ => "0x… recipient",
    };

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadBalanceAsync();
    }

    private async Task LoadBalanceAsync()
    {
        _balance.Text = "Balance: loading…";
        try
        {
            _balance.Text = $"Balance: {await ChainUnits.FormatBalanceAsync(_client, _wallet)}";
        }
        catch (Exception ex)
        {
            _balance.Text = $"Balance: unavailable ({ex.Message})";
        }
    }

    private void ConfigureSuiNetworkPicker()
    {
        if (Chain != "SUI") return;
        var networks = _client.Sui().AvailableNetworks();
        if (networks.Count == 0)
        {
            _networkWarning.IsVisible = true;
            _networkWarning.Text = "No Sui networks are configured.";
            _send.IsEnabled = false;
            return;
        }
        _suiNetwork.IsVisible = true;
        _suiNetwork.ItemsSource = networks.Select(network => network.Name).ToList();
        var active = _client.Wallets.ActiveSuiNetwork ?? networks.FirstOrDefault()?.Name;
        _suiNetwork.SelectedIndex = Math.Max(0, networks.FindIndex(network => network.Name == active));
        UpdateSuiFundingControls(networks[_suiNetwork.SelectedIndex]);
        _suiNetwork.SelectedIndexChanged += async (_, _) =>
        {
            if (_suiNetwork.SelectedIndex < 0) return;
            var network = networks[_suiNetwork.SelectedIndex];
            _client.Wallets.SetActiveSuiNetwork(network.Name);
            UpdateSuiFundingControls(network);
            await LoadBalanceAsync();
        };
    }

    private void UpdateSuiFundingControls(SuiNetwork network)
    {
        var isMainnet = network.Cluster == "mainnet";
        _faucet.IsVisible = !isMainnet;
        _networkWarning.IsVisible = isMainnet;
        _networkWarning.Text = isMainnet
            ? "Mainnet uses real SUI. The faucet is not available."
            : string.Empty;
    }

    private async void OnSend(object? sender, EventArgs e)
    {
        var to = _to.Text?.Trim();
        if (string.IsNullOrEmpty(to)) return;
        _busy.IsRunning = true;
        _send.IsEnabled = false;
        _result.Text = string.Empty;
        _status.Text = string.Empty;
        try
        {
            if (!await StepUpHelper.EnsureAsync(this, _client, "wallet:sign")) return;
            var check = await _client.Auth.CheckStepUpAsync("wallet:sign");
            if (check.IsRequired)
            {
                _result.Text = "Step-up required — not wired in this demo screen";
                return;
            }
            var units = ChainUnits.TokenToUnits(Chain, _amount.Text?.Trim() ?? "0");
            if (units <= 0) throw new DynamicStateException("Amount must be greater than zero");
            // Explicit per-chain branches, no EVM default — CreateWallet's own
            // chain list includes TON/TRON too (DESIGN.md §9 excludes both
            // from this pass), and an unhandled chain must fail loudly here
            // rather than silently get routed through an EvmTxRequest.
            var hash = Chain switch
            {
                "EVM" => await _client.Evm().SendTransactionAsync(new EvmTxRequest(from: _wallet.Address, to: to, value: units.ToString())),
                "SOL" => await _client.Solana().SendTransactionAsync(new SolanaTxRequest(from: _wallet.Address, to: to, lamports: units.ToString())),
                "BTC" => await _client.Btc().SendBitcoinAsync(_wallet.Address, to, checked((long)units)),
                "SUI" => await _client.Sui().SendTransactionAsync(new SuiTxRequest(from: _wallet.Address, to: to, mist: units.ToString())),
                _ => throw new DynamicStateException($"Send is not supported for chain {Chain} in this demo"),
            };
            _result.Text = $"Tx: {hash}";
            if (Chain == "SUI")
            {
                _status.Text = "Status: waiting for Sui indexing…";
                _status.Text = $"Status: {await WaitForSuiTransactionAsync(hash)}";
            }
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        catch (Exception ex) when (ex is OverflowException or FormatException)
        {
            // TokenToUnits/checked((long)units) on user-typed amount text —
            // report same as a DynamicException rather than letting it
            // escape this async void handler.
            await this.DisplayAlertAsync("Error", $"Invalid amount: {ex.Message}", "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _send.IsEnabled = true;
        }
    }

    private async Task<string> WaitForSuiTransactionAsync(string digest)
    {
        try
        {
            for (var attempt = 0; attempt < 10; attempt++)
            {
                var receipt = await _client.Sui().GetTransactionStatusAsync(digest);
                if (receipt != null)
                {
                    return receipt.Status
                        ? $"Succeeded at checkpoint {receipt.Checkpoint ?? "pending"}."
                        : $"Failed on-chain: {receipt.Err ?? "unknown execution error"}";
                }
                if (attempt < 9) await Task.Delay(1000);
            }
        }
        catch (Exception exception)
        {
            return $"Status lookup failed: {exception.Message}";
        }
        return "Pending: digest was broadcast but is not indexed yet.";
    }
}
