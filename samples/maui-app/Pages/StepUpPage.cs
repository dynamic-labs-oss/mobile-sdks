using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Standalone Step-Up Auth entry point (reachable from
/// Home/Profile) — the same step-up mechanism (StepUpHelper) already
/// triggered inline wherever a privileged action needs it (Sign Message /
/// Send / Export / business-account writes / ...), but here the user picks
/// a scope proactively and steps up before attempting anything gated by
/// it. Mirrors the Flutter reference's step_up_screen.dart.</summary>
public class StepUpPage : ContentPage
{
    // Scopes this demo actually exercises elsewhere, so stepping up here
    // then attempting the matching action elsewhere skips the inline prompt.
    private static readonly string[] Scopes = { "wallet:sign", "wallet:export", "credential:link", "credential:unlink" };

    private readonly DynamicClient _client;
    private readonly Picker _scopePicker = new() { Title = "Scope", ItemsSource = Scopes };
    private readonly Button _verify = new() { Text = "Verify" };
    private readonly ActivityIndicator _busy = new();
    private readonly Label _result = new() { LineBreakMode = LineBreakMode.CharacterWrap };

    public StepUpPage(DynamicClient client)
    {
        _client = client;
        Title = "Step-Up Auth";
        _scopePicker.SelectedIndex = 0;
        _verify.Clicked += OnVerify;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 24,
                Spacing = 16,
                Children =
                {
                    new Label { Text = "Scope", Style = AppStyles.CaptionLabel }, _scopePicker,
                    _verify, _busy, _result,
                },
            },
        };
    }

    private async void OnVerify(object? sender, EventArgs e)
    {
        var scope = (string)(_scopePicker.SelectedItem ?? Scopes[0]);
        _busy.IsRunning = true;
        _verify.IsEnabled = false;
        _result.Text = string.Empty;
        try
        {
            // CheckStepUp first, distinct from EnsureAsync's own internal
            // call, purely so this screen can say something truthful when
            // no step-up was actually needed — EnsureAsync's own true/false
            // return can't tell "already authorized" apart from "just
            // completed the ceremony", and silently reporting "stepped up"
            // for a scope that never required one would misreport what
            // happened.
            var check = await _client.Auth.CheckStepUpAsync(scope);
            if (!check.IsRequired)
            {
                _result.Text = $"Already authorized for \"{scope}\" — no step-up needed.";
                return;
            }
            var ok = await StepUpHelper.EnsureAsync(this, _client, scope);
            _result.Text = ok ? $"Stepped up for \"{scope}\"." : "Cancelled.";
        }
        finally
        {
            _busy.IsRunning = false;
            _verify.IsEnabled = true;
        }
    }
}
