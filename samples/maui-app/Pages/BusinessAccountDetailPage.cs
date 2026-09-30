using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Business account detail — members (role/remove/transfer-
/// ownership), linked wallets (link/remove), signers (remove). Kept as one
/// screen with simple card sections per CAPABILITIES.md ("it's fine to keep
/// the detail screen's member/wallet/signer sub-lists simple... rather than
/// separate sub-screens").</summary>
public class BusinessAccountDetailPage : ContentPage
{
    private readonly DynamicClient _client;
    private readonly string _businessAccountId;

    private readonly Label _nameLabel = new() { Style = AppStyles.SectionHeading };
    private readonly VerticalStackLayout _membersList = new() { Spacing = 8 };
    private readonly VerticalStackLayout _walletsList = new() { Spacing = 8 };
    private readonly VerticalStackLayout _signersList = new() { Spacing = 8 };
    private readonly ActivityIndicator _busy = new() { IsRunning = true };
    private readonly Label _error = new() { Style = AppStyles.ErrorLabel, IsVisible = false };

    public BusinessAccountDetailPage(DynamicClient client, string businessAccountId)
    {
        _client = client;
        _businessAccountId = businessAccountId;
        Title = "Business account";

        var rename = new Button { Text = "Rename" };
        rename.Clicked += OnRename;

        var addMember = new Button { Text = "Add member" };
        addMember.Clicked += OnAddMember;

        var linkWallet = new Button { Text = "Link wallet" };
        linkWallet.Clicked += OnLinkWallet;

        Content = new ScrollView
        {
            Content = new VerticalStackLayout
            {
                Padding = 16,
                Spacing = 16,
                Children =
                {
                    _nameLabel, _busy, _error, rename,
                    new Label { Text = "Members", Style = AppStyles.ListItemTitle }, _membersList, addMember,
                    new Label { Text = "Linked wallets", Style = AppStyles.ListItemTitle }, _walletsList, linkWallet,
                    new Label { Text = "Signers", Style = AppStyles.ListItemTitle }, _signersList,
                },
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
        try
        {
            var detail = await _client.BusinessAccount().GetBusinessAccountAsync(_businessAccountId);
            _nameLabel.Text = detail.Name ?? detail.Id;
            RenderMembers(detail.Members);
            RenderWallets(detail.Wallets ?? new List<BusinessAccountWallet>());
            RenderSigners(detail.Signers);
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

    private void RenderMembers(List<BusinessAccountMember> members)
    {
        _membersList.Children.Clear();
        if (members.Count == 0)
        {
            _membersList.Children.Add(new Label { Text = "No members.", Style = AppStyles.CaptionLabel });
            return;
        }
        foreach (var member in members)
        {
            var promote = new Button { Text = "Make owner", TextColor = AppColors.TxtLink };
            promote.Clicked += async (_, _) =>
            {
                var confirmed = await this.DisplayAlertAsync("Transfer ownership", $"Make {member.UserId} the owner? This signs you out.", "Transfer", "Cancel");
                if (!confirmed) return;
                if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:transfer_ownership")) return;
                try
                {
                    var result = await _client.BusinessAccount().TransferBusinessAccountOwnershipAsync(_businessAccountId, member.UserId);
                    if (result is BusinessAccountResult.ActionRequired)
                    {
                        await this.DisplayAlertAsync("Approval required", "The ownership transfer needs approval.", "OK");
                        return;
                    }
                    // A successful transfer revokes the current session.
                    try { await _client.Auth.SignOutAsync(); } catch { /* best-effort */ }
                    App.SetRoot(AppStyles.Wrap(new LoginPage(_client)));
                }
                catch (DynamicException ex)
                {
                    await this.DisplayAlertAsync("Error", ex.Message, "OK");
                }
            };

            var changeRole = member.Role == "owner" ? null : new Button { Text = "Change role" };
            if (changeRole != null)
            {
                changeRole.Clicked += async (_, _) =>
                {
                    var newRole = await this.DisplayActionSheetAsync("Change role", "Cancel", null, "admin", "viewer");
                    if (string.IsNullOrEmpty(newRole) || newRole == "Cancel" || newRole == member.Role) return;
                    if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:member:role:update")) return;
                    try
                    {
                        await _client.BusinessAccount().UpdateBusinessAccountMemberRoleAsync(_businessAccountId, member.UserId, newRole);
                        await RefreshAsync();
                    }
                    catch (DynamicException ex)
                    {
                        await this.DisplayAlertAsync("Error", ex.Message, "OK");
                    }
                };
            }

            var remove = new Button { Text = "Remove", TextColor = AppColors.Red };
            remove.Clicked += async (_, _) =>
            {
                if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:member:remove")) return;
                try
                {
                    await _client.BusinessAccount().RemoveBusinessAccountMemberAsync(_businessAccountId, member.UserId);
                    await RefreshAsync();
                }
                catch (DynamicException ex)
                {
                    await this.DisplayAlertAsync("Error", ex.Message, "OK");
                }
            };

            var memberActions = new HorizontalStackLayout { Spacing = 8, Children = { promote, remove } };
            if (changeRole != null) memberActions.Children.Insert(0, changeRole);

            _membersList.Children.Add(new Border
            {
                Padding = 12,
                StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
                Content = new VerticalStackLayout
                {
                    Spacing = 4,
                    Children =
                    {
                        new Label { Text = member.UserId, Style = AppStyles.ListItemTitle },
                        new Label { Text = $"role: {member.Role}", Style = AppStyles.CaptionLabel },
                        memberActions,
                    },
                },
            });
        }
    }

    private void RenderWallets(List<BusinessAccountWallet> wallets)
    {
        _walletsList.Children.Clear();
        if (wallets.Count == 0)
        {
            _walletsList.Children.Add(new Label { Text = "No linked wallets.", Style = AppStyles.CaptionLabel });
            return;
        }
        foreach (var wallet in wallets)
        {
            var remove = new Button { Text = "Unlink", TextColor = AppColors.Red };
            remove.Clicked += async (_, _) =>
            {
                if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:wallet:remove")) return;
                try
                {
                    await _client.BusinessAccount().RemoveBusinessAccountWalletAsync(_businessAccountId, wallet.Id);
                    await RefreshAsync();
                }
                catch (DynamicException ex)
                {
                    await this.DisplayAlertAsync("Error", ex.Message, "OK");
                }
            };

            _walletsList.Children.Add(new Border
            {
                Padding = 12,
                StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
                Content = new VerticalStackLayout
                {
                    Spacing = 4,
                    Children =
                    {
                        new Label { Text = $"{wallet.Chain} · {wallet.PublicKey}", Style = AppStyles.ListItemTitle, LineBreakMode = LineBreakMode.CharacterWrap },
                        remove,
                    },
                },
            });
        }
    }

    private void RenderSigners(List<BusinessAccountSigner> signers)
    {
        _signersList.Children.Clear();
        if (signers.Count == 0)
        {
            _signersList.Children.Add(new Label { Text = "No signers.", Style = AppStyles.CaptionLabel });
            return;
        }
        foreach (var signer in signers)
        {
            var remove = new Button { Text = "Remove", TextColor = AppColors.Red };
            remove.Clicked += async (_, _) =>
            {
                if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:signer:remove")) return;
                try
                {
                    await _client.BusinessAccount().RemoveBusinessAccountSignerAsync(_businessAccountId, signer.WalletId, signer.Id);
                    await RefreshAsync();
                }
                catch (DynamicException ex)
                {
                    await this.DisplayAlertAsync("Error", ex.Message, "OK");
                }
            };

            _signersList.Children.Add(new Border
            {
                Padding = 12,
                StrokeShape = new Microsoft.Maui.Controls.Shapes.RoundRectangle { CornerRadius = 10 },
                Content = new VerticalStackLayout
                {
                    Spacing = 4,
                    Children =
                    {
                        new Label { Text = $"{signer.Type} · wallet {signer.WalletId}", Style = AppStyles.ListItemTitle, LineBreakMode = LineBreakMode.CharacterWrap },
                        remove,
                    },
                },
            });
        }
    }

    private async void OnRename(object? sender, EventArgs e)
    {
        var name = await DisplayPromptAsync("Rename business account", "New name");
        if (string.IsNullOrWhiteSpace(name)) return;
        try
        {
            await _client.BusinessAccount().UpdateBusinessAccountAsync(_businessAccountId, name);
            await RefreshAsync();
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
    }

    private async void OnAddMember(object? sender, EventArgs e)
    {
        var identifier = await DisplayPromptAsync("Add member", "Email address");
        if (string.IsNullOrWhiteSpace(identifier)) return;
        var role = await this.DisplayActionSheetAsync("Role", "Cancel", null, "admin", "viewer");
        if (string.IsNullOrEmpty(role) || role == "Cancel") return;

        if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:member:add")) return;
        try
        {
            await _client.BusinessAccount().AddBusinessAccountMemberAsync(
                _businessAccountId,
                new BusinessAccountTargetIdentity(identifier: identifier, identifierType: "email"),
                role);
            await RefreshAsync();
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
    }

    private async void OnLinkWallet(object? sender, EventArgs e)
    {
        var wallets = _client.Wallets.UserWallets;
        if (wallets.Count == 0)
        {
            await this.DisplayAlertAsync("No wallets", "Create an embedded wallet first.", "OK");
            return;
        }
        var options = wallets.Select(w => $"{w.Chain} · {w.Address}").ToArray();
        var choice = await this.DisplayActionSheetAsync("Link wallet", "Cancel", null, options);
        if (string.IsNullOrEmpty(choice) || choice == "Cancel") return;
        var index = Array.IndexOf(options, choice);
        if (index < 0) return;

        if (!await StepUpHelper.EnsureAsync(this, _client, "business_account:link_wallet")) return;
        try
        {
            await _client.BusinessAccount().AddWalletToBusinessAccountAsync(wallets[index].Id, _businessAccountId);
            await RefreshAsync();
        }
        catch (DynamicException ex)
        {
            await this.DisplayAlertAsync("Error", ex.Message, "OK");
        }
    }
}
