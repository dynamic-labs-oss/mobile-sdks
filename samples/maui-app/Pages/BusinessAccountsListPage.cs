using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Business Accounts list — businessAccount.listBusinessAccounts,
/// createBusinessAccount. List-item-row pattern (DESIGN.md §7): a "Create
/// business account" CTA at the top, loading/error/empty states per the
/// standard pattern, tap a row to open BusinessAccountDetailPage.</summary>
public class BusinessAccountsListPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly VerticalStackLayout _list = new() { Spacing = 12 };
    private readonly Button _create = new() { Text = "Create business account" };
    private readonly ActivityIndicator _busy = new() { IsRunning = true };
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };

    public BusinessAccountsListPage(DynamicClient client)
    {
        _client = client;
        Title = "Business accounts";
        _create.Clicked += OnCreate;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children = { _create, _busy, _error, _list },
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
            var accounts = await _client.BusinessAccount().ListBusinessAccountsAsync();
            var items = accounts.Items ?? new List<BusinessAccount>();
            if (items.Count == 0)
            {
                _list.Children.Add(new Border
                {
                    Padding = 16,
                    StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
                    Content = new VerticalStackLayout
                    {
                        Spacing = 4,
                        Children =
                        {
                            new Label { Text = "No business accounts configured", FontAttributes = FontAttributes.Bold },
                            new Label { Text = "Create one above to share a wallet across multiple operators.", Style = AppStyles.CaptionLabel },
                        },
                    },
                });
            }
            else
            {
                foreach (var account in items) _list.Children.Add(BuildCard(account));
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

    private View BuildCard(BusinessAccount account)
    {
        var border = new Border
        {
            Padding = 16,
            StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
            Content = new VerticalStackLayout
            {
                Spacing = 4,
                Children =
                {
                    new Label { Text = account.Name ?? account.Id, Style = AppStyles.ListItemTitle },
                    new Label { Text = $"id: {account.Id}", Style = AppStyles.CaptionLabel },
                },
            },
        };
        var tap = new TapGestureRecognizer();
        tap.Tapped += async (_, _) => await Navigation.PushAsync(new BusinessAccountDetailPage(_client, account.Id));
        border.GestureRecognizers.Add(tap);
        return border;
    }

    private async void OnCreate(object? sender, EventArgs e)
    {
        var name = await DisplayPromptAsync("Create business account", "Name (optional)");
        _busy.IsRunning = true;
        _create.IsEnabled = false;
        _error.IsVisible = false;
        try
        {
            var account = await _client.BusinessAccount().CreateBusinessAccountAsync(string.IsNullOrWhiteSpace(name) ? null : name, null);
            await Navigation.PushAsync(new BusinessAccountDetailPage(_client, account.Id));
        }
        catch (DynamicException ex)
        {
            _error.Text = ex.Message;
            _error.IsVisible = true;
        }
        finally
        {
            _busy.IsRunning = false;
            _create.IsEnabled = true;
        }
    }
}
