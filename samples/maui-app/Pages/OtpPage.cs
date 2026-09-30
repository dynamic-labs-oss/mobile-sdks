using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Email OTP verification.</summary>
public class OtpPage : ContentPage
{
    private readonly DynamicClient _client;
    private string _verificationUuid;
    private readonly string _email;
    private readonly Entry _code = new() { Placeholder = "123456", Keyboard = Keyboard.Numeric, MaxLength = 6 };
    private readonly Button _verify = new() { Text = "Verify" };
    private readonly Button _resend = new() { Text = "Resend code" };
    private readonly ActivityIndicator _busy = new();

    public OtpPage(DynamicClient client, string verificationUuid, string email)
    {
        _client = client;
        _verificationUuid = verificationUuid;
        _email = email;
        Title = "Enter code";
        _verify.Clicked += OnVerify;
        _resend.Clicked += OnResend;
        Content = new VerticalStackLayout
        {
            Padding = 24,
            Spacing = 16,
            Children =
            {
                new Label { Text = $"Code sent to {email}" }, _code, _verify, _resend, _busy,
            },
        };
    }

    private async void OnVerify(object? sender, EventArgs e)
    {
        var code = _code.Text?.Trim();
        if (string.IsNullOrEmpty(code)) return;
        _busy.IsRunning = true;
        _verify.IsEnabled = false;
        try
        {
            await _client.Auth.VerifyEmailOtpAsync(_verificationUuid, code);
            var user = await _client.Auth.RefreshUserAsync();
            // No explicit auto-create-wallets call needed: WaasExtension
            // subscribes to UserChanged and syncs missing wallets in the
            // background — mirrors dynamic-auth's useSyncDynamicWaas.
            App.SetRoot(AppStyles.Wrap(new HomePage(_client, user)));
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
        finally
        {
            _busy.IsRunning = false;
            _verify.IsEnabled = true;
        }
    }

    private async void OnResend(object? sender, EventArgs e)
    {
        try
        {
            var result = await _client.Auth.ResendEmailOtpAsync(_verificationUuid, _email);
            _verificationUuid = result.VerificationUuid;
            await this.DisplayAlertAsync("Sent", "A new code was sent.", "OK");
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
    }
}
