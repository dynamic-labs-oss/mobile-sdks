using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Wallet password management, reachable from Wallet Details:
/// waas.getWalletRecoveryState / unlockWallet / setWaasWalletAccountPassword
/// / updateWaasPassword. Shows the current recovery state, an unlock form
/// if locked, and a set/change-password form otherwise.</summary>
public class WalletPasswordPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly Wallet _wallet;

    private readonly Label _stateLabel = new() { Text = "Loading…" };
    private readonly ActivityIndicator _busy = new() { IsRunning = true };
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };

    // Unlock form (shown when the wallet is password-encrypted and this
    // session hasn't unlocked it yet).
    private readonly VerticalStackLayout _unlockForm;
    private readonly Entry _unlockPassword = new() { Placeholder = "Password", IsPassword = true };
    private readonly Button _unlock = new() { Text = "Unlock" };

    // Set/change-password form.
    private readonly VerticalStackLayout _setForm;
    private readonly Entry _existingPassword = new() { Placeholder = "Current password", IsPassword = true };
    private readonly Entry _newPassword = new() { Placeholder = "New password", IsPassword = true };
    private readonly Entry _confirmPassword = new() { Placeholder = "Confirm new password", IsPassword = true };
    private readonly Button _save = new() { Text = "Save password" };

    private WalletRecoveryState? _recoveryState;

    public WalletPasswordPage(DynamicClient client, Wallet wallet)
    {
        _client = client;
        _wallet = wallet;
        Title = "Wallet password";

        _unlock.Clicked += OnUnlock;
        _unlockForm = new VerticalStackLayout
        {
            Spacing = 8,
            IsVisible = false,
            Children = { new Label { Text = "This wallet is locked for this session.", Style = AppStyles.CaptionLabel }, _unlockPassword, _unlock },
        };

        _save.Clicked += OnSave;
        _setForm = new VerticalStackLayout
        {
            Spacing = 8,
            IsVisible = false,
            Children = { _existingPassword, _newPassword, _confirmPassword, _save },
        };

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 24,
                Spacing = 16,
                Children =
                {
                    new Label { Text = $"{_wallet.Chain} · {_wallet.Address}", LineBreakMode = LineBreakMode.CharacterWrap },
                    _stateLabel, _busy, _error,
                    _unlockForm, _setForm,
                },
            },
        };
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await RefreshStateAsync();
    }

    private async Task RefreshStateAsync()
    {
        _busy.IsRunning = true;
        _error.IsVisible = false;
        try
        {
            _recoveryState = await _client.Waas().GetWalletRecoveryStateAsync(_wallet.Chain, _wallet.Address);
            _stateLabel.Text = _recoveryState.IsPasswordEncrypted
                ? $"Password-protected — wallet ready state: {_recoveryState.WalletReadyState}"
                : "Not password-protected.";

            // The engine reports the READY state (e.g. locked vs. unlocked)
            // via WalletReadyState; the exact vocabulary is engine-defined,
            // so this treats anything other than an explicit "ready" as
            // needing an unlock first, matching getWalletRecoveryState's
            // own doc ("call before signing when the wallet MIGHT be
            // password-protected, to know whether unlockWallet needs to run").
            var needsUnlock = _recoveryState.IsPasswordEncrypted && _recoveryState.WalletReadyState != "ready";
            _unlockForm.IsVisible = needsUnlock;
            _setForm.IsVisible = !needsUnlock;
            _existingPassword.IsVisible = _recoveryState.IsPasswordEncrypted;
            _save.Text = _recoveryState.IsPasswordEncrypted ? "Change password" : "Set password";
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
        }
    }

    private async void OnUnlock(object? sender, EventArgs e)
    {
        var password = _unlockPassword.Text ?? string.Empty;
        if (string.IsNullOrEmpty(password)) return;

        _busy.IsRunning = true;
        _unlock.IsEnabled = false;
        _error.IsVisible = false;
        try
        {
            await _client.Waas().UnlockWalletAsync(_wallet.Chain, _wallet.Address, password);
            await RefreshStateAsync();
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
            _unlock.IsEnabled = true;
        }
    }

    private async void OnSave(object? sender, EventArgs e)
    {
        var newPassword = _newPassword.Text ?? string.Empty;
        _error.IsVisible = false;
        if (string.IsNullOrEmpty(newPassword) || newPassword != _confirmPassword.Text)
        {
            _error.Text = "Passwords must match and not be empty.";
            _error.IsVisible = true;
            return;
        }

        _busy.IsRunning = true;
        _save.IsEnabled = false;
        try
        {
            var isPasswordEncrypted = _recoveryState?.IsPasswordEncrypted ?? false;
            if (isPasswordEncrypted)
            {
                await _client.Waas().UpdateWaasPasswordAsync(_wallet.Chain, _wallet.Address, _existingPassword.Text ?? string.Empty, newPassword);
            }
            else
            {
                await _client.Waas().SetWaasWalletAccountPasswordAsync(_wallet.Chain, _wallet.Address, newPassword);
            }
            await RefreshStateAsync();
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
            _save.IsEnabled = true;
        }
    }
}
