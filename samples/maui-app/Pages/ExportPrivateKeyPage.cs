using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Reveals a wallet's private key, gated by step-up. Mirrors
/// SignMessagePage.cs's shape. Unlike signing, the reveal UI is NOT drawn by
/// this page: ExportPrivateKey() attaches its own native, screenshot-protected
/// overlay directly on top of the app for the duration of the call (see
/// DynamicWaasEngine.exportPrivateKey's doc) and resolves once the user has
/// viewed or dismissed it — this page never sees key material.</summary>
public class ExportPrivateKeyPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly Wallet _wallet;
    private readonly Button _export = new() { Text = "Reveal private key" };
    private readonly ActivityIndicator _busy = new();
    private readonly Label _status = new() { LineBreakMode = LineBreakMode.CharacterWrap };

    public ExportPrivateKeyPage(DynamicClient client, Wallet wallet)
    {
        _client = client;
        _wallet = wallet;
        Title = "Export private key";
        _export.Clicked += OnExport;
        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children =
                {
                    new Label { Text = $"{_wallet.Chain} · {_wallet.Address}", LineBreakMode = LineBreakMode.CharacterWrap },
                    new Label
                    {
                        Text = "Your private key gives full control over this wallet. Dynamic reveals " +
                               "it directly on screen — never to this app or its developer. Don't share " +
                               "it or take a screenshot.",
                    },
                    _export, _busy, _status,
                },
            },
        };
    }

    private async void OnExport(object? sender, EventArgs e)
    {
        // Elevated-access gate before the reveal, same shape as SignMessagePage's.
        if (!await StepUpHelper.EnsureAsync(this, _client, "wallet:export")) return;

        _busy.IsRunning = true;
        _export.IsEnabled = false;
        _status.Text = string.Empty;
        try
        {
            // Wallet-scoped call — the object already in hand, no re-lookup.
            await _wallet.ExportPrivateKeyAsync();
            _status.Text = "Done — the key was shown once and is not stored here.";
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _export.IsEnabled = true;
        }
    }
}
