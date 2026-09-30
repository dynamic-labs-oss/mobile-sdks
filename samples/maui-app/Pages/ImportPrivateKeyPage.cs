using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Import Private Key — waas.importPrivateKey(chain, privateKey,
/// isRawScalarImport?). Chain picker (BTC/EVM/Solana/Sui), a private-key
/// field, submit. isRawScalarImport only applies to the ed25519 chains
/// (SOL/SUI) — the toggle only shows for those two.</summary>
public class ImportPrivateKeyPage : ContentPage
{
    private static readonly string[] Chains = { "EVM", "SOL", "BTC", "SUI" };
    private static readonly string[] RawScalarChains = { "SOL", "SUI" };

    private readonly DynamicClient _client;
    private readonly Picker _chainPicker = new() { Title = "Chain", ItemsSource = Chains };
    private readonly Entry _privateKey = new() { Placeholder = "Private key", IsPassword = true };
    private readonly Switch _rawScalarSwitch = new();
    private readonly HorizontalStackLayout _rawScalarRow;
    private readonly Button _submit = new() { Text = "Import" };
    private readonly ActivityIndicator _busy = new();
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };

    public ImportPrivateKeyPage(DynamicClient client)
    {
        _client = client;
        Title = "Import private key";
        _chainPicker.SelectedIndex = 0;

        _rawScalarRow = new HorizontalStackLayout
        {
            Spacing = 8,
            Children = { _rawScalarSwitch, new Label { Text = "Raw ed25519 scalar (not a seed)", VerticalOptions = LayoutOptions.Center } },
        };
        UpdateRawScalarVisibility();
        _chainPicker.SelectedIndexChanged += (_, _) => UpdateRawScalarVisibility();

        _submit.Clicked += OnSubmit;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 24,
                Spacing = 16,
                Children =
                {
                    new Label { Text = "Chain", Style = AppStyles.CaptionLabel }, _chainPicker,
                    new Label { Text = "Private key", Style = AppStyles.CaptionLabel }, _privateKey,
                    _rawScalarRow,
                    _error,
                    _submit, _busy,
                },
            },
        };
    }

    private void UpdateRawScalarVisibility()
    {
        var chain = (string)(_chainPicker.SelectedItem ?? "EVM");
        _rawScalarRow.IsVisible = RawScalarChains.Contains(chain);
        if (!_rawScalarRow.IsVisible) _rawScalarSwitch.IsToggled = false;
    }

    private async void OnSubmit(object? sender, EventArgs e)
    {
        var chain = (string)(_chainPicker.SelectedItem ?? "EVM");
        var privateKey = _privateKey.Text?.Trim();
        _error.IsVisible = false;
        if (string.IsNullOrEmpty(privateKey))
        {
            _error.Text = "Private key is required.";
            _error.IsVisible = true;
            return;
        }

        bool? isRawScalarImport = _rawScalarRow.IsVisible ? _rawScalarSwitch.IsToggled : null;

        _busy.IsRunning = true;
        _submit.IsEnabled = false;
        try
        {
            await _client.Waas().ImportPrivateKeyAsync(chain, privateKey, isRawScalarImport);
            await Navigation.PopAsync();
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
            _submit.IsEnabled = true;
        }
    }
}
