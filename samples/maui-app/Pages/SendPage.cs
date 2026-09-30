using Dynamic.Sdk.Btc;
using Dynamic.Sdk;

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
    private readonly ActivityIndicator _busy = new();

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
                    new Label { Text = "To" }, _to,
                    new Label { Text = $"Amount ({UnitName})" }, _amount,
                    _send, _busy,
                    new Label { Text = "Result", FontAttributes = FontAttributes.Bold }, _result,
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
        try
        {
            _balance.Text = $"Balance: {await ChainUnits.FormatBalanceAsync(_client, _wallet)}";
        }
        catch (Exception ex)
        {
            _balance.Text = $"Balance: unavailable ({ex.Message})";
        }
    }

    private async void OnSend(object? sender, EventArgs e)
    {
        var to = _to.Text?.Trim();
        if (string.IsNullOrEmpty(to)) return;
        _busy.IsRunning = true;
        _send.IsEnabled = false;
        _result.Text = string.Empty;
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
}
