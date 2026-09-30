using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>What this screen signs: a plain message (every chain, via
/// Wallet.SignMessage) or a raw, already-serialized unsigned transaction
/// blob (BTC PSBT / Sui transaction bytes, via client.Waas().SignTransaction
/// directly — chain extensions don't expose a bare "sign, don't broadcast"
/// entry point of their own). See CAPABILITIES.md's BTC/Sui coverage item:
/// reuse this screen's chain-branching instead of building a new one per
/// chain.</summary>
public enum SignKind
{
    Message,
    RawTransaction,
}

/// <summary>Sign a message (or, for BTC/Sui, a raw unsigned transaction
/// blob) with an embedded wallet, gated by step-up.</summary>
public class SignMessagePage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly Wallet _wallet;
    private readonly SignKind _kind;
    private readonly Editor _message = new() { HeightRequest = 100 };
    private readonly Button _sign;
    private readonly ActivityIndicator _busy = new();
    private readonly Label _result = new() { LineBreakMode = LineBreakMode.CharacterWrap };

    public SignMessagePage(DynamicClient client, Wallet wallet, SignKind kind = SignKind.Message)
    {
        _client = client;
        _wallet = wallet;
        _kind = kind;

        var isRaw = kind == SignKind.RawTransaction;
        var isBtc = wallet.Chain == "BTC";
        Title = isRaw ? (isBtc ? "Sign PSBT" : "Sign transaction") : "Sign message";
        _message.Placeholder = isRaw
            ? (isBtc ? "Unsigned PSBT (base64)" : "Unsigned transaction (base64/hex)")
            : "Message to sign";
        if (!isRaw) _message.Text = "Hello from Dynamic MAUI";

        _sign = new Button { Text = isRaw ? (isBtc ? "Sign PSBT" : "Sign transaction") : "Sign message" };
        _sign.Clicked += OnSign;
        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children =
                {
                    new Label { Text = $"{_wallet.Chain} · {_wallet.Address}", LineBreakMode = LineBreakMode.CharacterWrap },
                    _message, _sign, _busy,
                    new Label { Text = "Signature", FontAttributes = FontAttributes.Bold },
                    _result,
                },
            },
        };
    }

    private async void OnSign(object? sender, EventArgs e)
    {
        var message = _message.Text?.Trim();
        if (string.IsNullOrEmpty(message)) return;
        // Elevated-access gate before the signing ceremony.
        if (!await StepUpHelper.EnsureAsync(this, _client, "wallet:sign")) return;

        _busy.IsRunning = true;
        _sign.IsEnabled = false;
        _result.Text = string.Empty;
        try
        {
            var signature = _kind == SignKind.RawTransaction
                // Raw signing has no chain-extension convenience method (no
                // "build unsigned, don't broadcast" step exists for BTC/Sui)
                // — go straight to the generic WaaS sign, same one every
                // chain extension's own SendTransaction/SendBitcoin uses
                // internally.
                ? await _client.Waas().SignTransactionAsync(_wallet.Chain, _wallet.Address, message, null, null, null)
                // Wallet-scoped call — the object already in hand, no re-lookup.
                : await _wallet.SignMessageAsync(message);
            _result.Text = signature;
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _sign.IsEnabled = true;
        }
    }
}
