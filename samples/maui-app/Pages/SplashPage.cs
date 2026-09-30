using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Boot: initialize the SDK, then route to Home (if signed in) or Login.</summary>
public class SplashPage : ContentPage
{
    private readonly DynamicClient _client;

    public SplashPage(DynamicClient client)
    {
        _client = client;
        // Full-screen login_background.png (pulled from the Dynamic Flutter
        // SDK example app's assets/splash.png) — matches its own
        // SplashScreen, which is JUST this image; the activity indicator is
        // this demo's own addition on top of it, since our boot does real
        // async work the reference's stream-driven splash doesn't need.
        Content = new Grid
        {
            Children =
            {
                new Image { Source = "login_background.png", Aspect = Aspect.AspectFill },
                new ActivityIndicator
                {
                    IsRunning = true,
                    HorizontalOptions = LayoutOptions.Center,
                    VerticalOptions = LayoutOptions.Center,
                },
            },
        };
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        try
        {
            await _client.InitializeAsync();
            var token = _client.Auth.AuthToken;
            if (token is not null)
            {
                var user = await _client.Auth.RefreshUserAsync();
                // No explicit auto-create-wallets call needed — see OtpPage's comment.
                App.SetRoot(AppStyles.Wrap(new HomePage(_client, user)));
            }
            else
            {
                App.SetRoot(AppStyles.Wrap(new LoginPage(_client)));
            }
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Startup error", ex.Message, "OK");
            App.SetRoot(AppStyles.Wrap(new LoginPage(_client)));
        }
    }
}
