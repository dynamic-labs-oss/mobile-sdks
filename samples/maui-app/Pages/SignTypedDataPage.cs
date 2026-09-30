using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Sign Typed Data (EVM only) — waas.signTypedData. Same skeleton
/// as SignMessagePage (JSON field → step-up-gated Sign → result card), but
/// only offered from an EVM wallet's Wallet Details screen, alongside the
/// existing Sign Message entry.</summary>
public class SignTypedDataPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly Wallet _wallet;
    private readonly Editor _typedData = new() { Placeholder = "EIP-712 typed data (JSON)", HeightRequest = 200 };
    private readonly Button _sign = new() { Text = "Sign typed data" };
    private readonly ActivityIndicator _busy = new();
    private readonly Label _result = new() { LineBreakMode = LineBreakMode.CharacterWrap };

    public SignTypedDataPage(DynamicClient client, Wallet wallet)
    {
        _client = client;
        _wallet = wallet;
        Title = "Sign typed data";
        _typedData.Text = """
        {"domain":{"name":"Dynamic MAUI Demo","version":"1","chainId":11155111},"primaryType":"Mail","types":{"EIP712Domain":[{"name":"name","type":"string"},{"name":"version","type":"string"},{"name":"chainId","type":"uint256"}],"Mail":[{"name":"contents","type":"string"}]},"message":{"contents":"Hello from Dynamic MAUI"}}
        """;
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
                    _typedData, _sign, _busy,
                    new Label { Text = "Signature", FontAttributes = FontAttributes.Bold },
                    _result,
                },
            },
        };
    }

    private async void OnSign(object? sender, EventArgs e)
    {
        var typedData = _typedData.Text?.Trim();
        if (string.IsNullOrEmpty(typedData)) return;
        // Elevated-access gate before the signing ceremony, same shape as
        // SignMessagePage's.
        if (!await StepUpHelper.EnsureAsync(this, _client, "wallet:sign")) return;

        _busy.IsRunning = true;
        _sign.IsEnabled = false;
        _result.Text = string.Empty;
        try
        {
            var signature = await _client.Waas().SignTypedDataAsync(_wallet.Chain, _wallet.Address, typedData);
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
