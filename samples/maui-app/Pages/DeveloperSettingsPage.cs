namespace DynamicMauiDemo;

/// <summary>Hidden developer tools, reachable by tapping the login title 7
/// times (see SecretTapDetector). Lets a developer point the demo at a
/// different environment id / API base url and rebuild the app
/// (App.RestartWithCurrentSettings) so a fresh client picks them up — no
/// rebuild, no process relaunch. C# peer of the Flutter demo's
/// screens/developer_settings_screen.dart.</summary>
public class DeveloperSettingsPage : ContentPage
{
    private readonly Entry _environmentId = new() { Text = DevSettings.EnvironmentId };
    private readonly Entry _apiBaseUrl = new() { Text = DevSettings.ApiBaseUrl, Keyboard = Keyboard.Url };

    public DeveloperSettingsPage()
    {
        Title = "Developer Tools";

        var apply = new Button { Text = "Apply & Restart" };
        apply.Clicked += OnApply;

        var reset = new Button { Text = "Reset to defaults" };
        reset.Clicked += (_, _) =>
        {
            _environmentId.Text = DevSettings.DefaultEnvironmentId;
            _apiBaseUrl.Text = DevSettings.DefaultApiBaseUrl;
        };

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children =
                {
                    new Label { Text = "Environment ID" }, _environmentId,
                    new Label { Text = "API Base URL" }, _apiBaseUrl,
                    apply, reset,
                },
            },
        };
    }

    private async void OnApply(object? sender, EventArgs e)
    {
        var environmentId = _environmentId.Text?.Trim() ?? string.Empty;
        var apiBaseUrl = _apiBaseUrl.Text?.Trim() ?? string.Empty;
        if (string.IsNullOrEmpty(environmentId) || string.IsNullOrEmpty(apiBaseUrl))
        {
            await this.DisplayAlertAsync("Error", "Environment ID and API base URL are required", "OK");
            return;
        }

        DevSettings.EnvironmentId = environmentId;
        DevSettings.ApiBaseUrl = apiBaseUrl;
        App.RestartWithCurrentSettings();
    }
}
