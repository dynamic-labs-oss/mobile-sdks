using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>MFA recovery codes — auth.getMfaRecoveryCodes /
/// createNewMfaRecoveryCodes / acknowledgeMfaRecoveryCodes /
/// isPendingMfaRecoveryCodesAcknowledgment. Shows the current codes
/// (monospace, grey-200 chip per DESIGN.md §1), a "Generate new codes"
/// button, and an acknowledgment step gated behind
/// isPendingMfaRecoveryCodesAcknowledgment before the screen is
/// dismissible — blocks the platform back button/gesture the same as the
/// nav back button while acknowledgment is pending.</summary>
public class MfaRecoveryCodesPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly VerticalStackLayout _codesList = new() { Spacing = 8 };
    private readonly Button _generate = new() { Text = "Generate new codes" };
    private readonly Button _acknowledge = new() { Text = "I've saved these codes", Style = AppStyles.PillButton, IsVisible = false };
    private readonly ActivityIndicator _busy = new() { IsRunning = true };
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };
    private readonly Label _pendingBanner = new()
    {
        Text = "Save these codes somewhere safe before continuing — you won't be able to see them again.",
        TextColor = AppColors.Orange,
        IsVisible = false,
    };

    public MfaRecoveryCodesPage(DynamicClient client)
    {
        _client = client;
        Title = "Recovery codes";
        _generate.Clicked += OnGenerate;
        _acknowledge.Clicked += OnAcknowledge;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children = { _pendingBanner, _codesList, _busy, _error, _generate, _acknowledge },
            },
        };
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await RefreshAsync();
    }

    /// <summary>Blocks the hardware/gesture back navigation while
    /// acknowledgment is pending — paired with NavigationPage.SetHasBackButton
    /// (set in RefreshAsync) so both the nav-bar back button AND the
    /// platform back gesture/button are covered.</summary>
    protected override bool OnBackButtonPressed() => _client.Auth.IsPendingMfaRecoveryCodesAcknowledgment;

    private async Task RefreshAsync()
    {
        _busy.IsRunning = true;
        _error.IsVisible = false;
        _codesList.Children.Clear();
        try
        {
            var response = await _client.Auth.GetMfaRecoveryCodesAsync();
            RenderCodes(response.RecoveryCodes);
            UpdatePendingState();
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

    private void RenderCodes(List<string> codes)
    {
        _codesList.Children.Clear();
        foreach (var code in codes)
        {
            _codesList.Children.Add(new Border
            {
                Padding = 8,
                BackgroundColor = AppColors.Grey200,
                Stroke = AppColors.Grey200,
                StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 6 },
                Content = new Label { Text = code, FontFamily = "Courier" },
            });
        }
    }

    private void UpdatePendingState()
    {
        var pending = _client.Auth.IsPendingMfaRecoveryCodesAcknowledgment;
        _pendingBanner.IsVisible = pending;
        _acknowledge.IsVisible = pending;
        NavigationPage.SetHasBackButton(this, !pending);
    }

    private async void OnGenerate(object? sender, EventArgs e)
    {
        _busy.IsRunning = true;
        _generate.IsEnabled = false;
        _error.IsVisible = false;
        try
        {
            var response = await _client.Auth.CreateNewMfaRecoveryCodesAsync();
            RenderCodes(response.RecoveryCodes);
            UpdatePendingState();
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
            _generate.IsEnabled = true;
        }
    }

    private async void OnAcknowledge(object? sender, EventArgs e)
    {
        _acknowledge.IsEnabled = false;
        try
        {
            await _client.Auth.AcknowledgeMfaRecoveryCodesAsync();
            UpdatePendingState();
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _acknowledge.IsEnabled = true;
        }
    }
}
