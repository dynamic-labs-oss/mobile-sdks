using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Create Wallet as its own screen (promoted out of Home) — mirrors
/// the reference's dedicated create_wallet_screen.dart. Chain picker
/// (BTC/EVM/Solana/Sui — all four are Tier-1-supported, see
/// CAPABILITIES.md's chain-picker consistency fix), an optional
/// "protect with a password" switch that reveals a password + confirm
/// field, and a submit button using the pill/filled button style (DESIGN.md
/// §5.4) — the one FilledButton.icon moment in the reference.</summary>
public class CreateWalletPage : ContentPage
{
    private static readonly string[] Chains = { "EVM", "SOL", "BTC", "SUI" };

    private readonly DynamicClient _client;
    private readonly Picker _chainPicker = new() { Title = "Chain", ItemsSource = Chains };
    private readonly Switch _passwordSwitch = new();
    private readonly VerticalStackLayout _passwordFields;
    private readonly Entry _password = new() { Placeholder = "Password", IsPassword = true };
    private readonly Entry _confirmPassword = new() { Placeholder = "Confirm password", IsPassword = true };
    private readonly Button _submit = new() { Text = "Create wallet", Style = AppStyles.PillButton };
    private readonly ActivityIndicator _busy = new();
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };

    public CreateWalletPage(DynamicClient client)
    {
        _client = client;
        Title = "Create wallet";
        _chainPicker.SelectedIndex = 0;

        _passwordFields = new VerticalStackLayout
        {
            Spacing = 8,
            IsVisible = false,
            Children = { _password, _confirmPassword },
        };
        _passwordSwitch.Toggled += (_, e) => _passwordFields.IsVisible = e.Value;

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
                    new HorizontalStackLayout
                    {
                        Spacing = 8,
                        Children = { _passwordSwitch, new Label { Text = "Protect with a password", VerticalOptions = LayoutOptions.Center } },
                    },
                    _passwordFields,
                    _error,
                    _submit, _busy,
                },
            },
        };
    }

    private async void OnSubmit(object? sender, EventArgs e)
    {
        var chain = (string)(_chainPicker.SelectedItem ?? "EVM");
        _error.IsVisible = false;

        if (_passwordSwitch.IsToggled)
        {
            var password = _password.Text ?? string.Empty;
            if (string.IsNullOrEmpty(password) || password != _confirmPassword.Text)
            {
                _error.Text = "Passwords must match and not be empty.";
                _error.IsVisible = true;
                return;
            }
        }

        _busy.IsRunning = true;
        _submit.IsEnabled = false;
        try
        {
            var created = await _client.Waas().CreateWalletAsync(chain);
            if (_passwordSwitch.IsToggled)
            {
                await _client.Waas().SetWaasWalletAccountPasswordAsync(chain, created.AccountAddress, _password.Text ?? string.Empty);
            }
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
