using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Passkeys management — auth.getPasskeys / registerPasskey /
/// deletePasskey. List-item-row pattern (DESIGN.md §7): each passkey shows
/// alias/device info, a destructive/red delete button; a "Register
/// passkey" CTA at the top; loading/error/empty states per the standard
/// pattern.</summary>
public class PasskeysPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly VerticalStackLayout _list = new() { Spacing = 12 };
    private readonly Button _register = new() { Text = "Register passkey" };
    private readonly ActivityIndicator _busy = new() { IsRunning = true };
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };

    public PasskeysPage(DynamicClient client)
    {
        _client = client;
        Title = "Passkeys";
        _register.Clicked += OnRegister;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children = { _register, _busy, _error, _list },
            },
        };
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await RefreshAsync();
    }

    private async Task RefreshAsync()
    {
        _busy.IsRunning = true;
        _error.IsVisible = false;
        _list.Children.Clear();
        try
        {
            var passkeys = await _client.Auth.GetPasskeysAsync();
            if (passkeys.Count == 0)
            {
                _list.Children.Add(EmptyCard());
            }
            else
            {
                foreach (var passkey in passkeys) _list.Children.Add(BuildCard(passkey));
            }
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

    private static View EmptyCard() => new Border
    {
        Padding = 16,
        StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
        Content = new VerticalStackLayout
        {
            Spacing = 4,
            Children =
            {
                new Label { Text = "No passkeys configured", FontAttributes = FontAttributes.Bold },
                new Label { Text = "Register one above to sign in without a password.", Style = AppStyles.CaptionLabel },
            },
        },
    };

    private View BuildCard(UserPasskey passkey)
    {
        var delete = new Button { Text = "Delete", TextColor = AppColors.Red };
        delete.Clicked += async (_, _) =>
        {
            var confirmed = await this.DisplayAlertAsync("Delete passkey", "Remove this passkey from your account?", "Delete", "Cancel");
            if (!confirmed) return;
            try
            {
                await _client.Auth.DeletePasskeyAsync(passkey.Id);
                await RefreshAsync();
            }
            catch (DynamicException ex)
            {
                await this.DisplayAlertAsync("Error", ex.Message, "OK");
            }
        };

        return new Border
        {
            Padding = 16,
            StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
            Content = new VerticalStackLayout
            {
                Spacing = 8,
                Children =
                {
                    new Label { Text = passkey.Alias ?? "Passkey", Style = AppStyles.ListItemTitle },
                    new Label { Text = $"Created {passkey.CreatedAt}", Style = AppStyles.CaptionLabel },
                    new Label { Text = passkey.UserAgent ?? passkey.CredentialId, Style = AppStyles.CaptionLabel },
                    delete,
                },
            },
        };
    }

    private async void OnRegister(object? sender, EventArgs e)
    {
        _busy.IsRunning = true;
        _register.IsEnabled = false;
        _error.IsVisible = false;
        try
        {
            await _client.Auth.RegisterPasskeyAsync();
            await RefreshAsync();
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
            _register.IsEnabled = true;
        }
    }
}
