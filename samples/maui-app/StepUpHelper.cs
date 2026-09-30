using Dynamic.Sdk;

namespace DynamicMauiDemo;

/// <summary>Step-up (MFA) gate, mirroring the Flutter demo's step_up_modal.dart:
/// generic over whatever credentials the server reports for the scope — prefers
/// an already-registered TOTP device, falls back to email-OTP reauth when the
/// user has none (same has-MFA vs. no-MFA branch as dynamic-auth's
/// usePromptStepUpAuth). Other credential kinds aren't wired up yet.</summary>
public static class StepUpHelper
{
    public static async Task<bool> EnsureAsync(Page page, DynamicClient client, string scope)
    {
        var check = await client.Auth.CheckStepUpAsync(scope);
        if (!check.IsRequired) return true;

        var hasTotp = check.Credentials.Any(c => c.Type == "totp");
        var hasEmail = check.Credentials.Any(c => c.Format == "email");
        if (!hasTotp && !hasEmail)
        {
            await page.DisplayAlertAsync("Step-up required", "No supported step-up method for this demo.", "OK");
            return false;
        }

        try
        {
            return hasTotp
                ? await CompleteTotpAsync(page, client, scope)
                : await CompleteEmailOtpAsync(page, client, scope);
        }
        catch (DynamicException ex)
        {
            await page.DisplayAlertAsync("Step-up failed", ex.Message, "OK");
            return false;
        }
    }

    private static async Task<bool> CompleteTotpAsync(Page page, DynamicClient client, string scope)
    {
        var code = await page.DisplayPromptAsync("Verify it's you", "Enter your authenticator code",
            keyboard: Keyboard.Numeric, maxLength: 6);
        if (string.IsNullOrWhiteSpace(code)) return false;
        await client.Auth.CompleteTotpStepUpAsync(code.Trim(), scope);
        return true;
    }

    private static async Task<bool> CompleteEmailOtpAsync(Page page, DynamicClient client, string scope)
    {
        // The real widget auto-sends on mount; we do the same here, then
        // prompt straight for the code — the user never has to ask for it.
        var sent = await client.Auth.SendStepUpEmailOtpAsync();
        var code = await page.DisplayPromptAsync("Verify it's you", "Enter the code sent to your email",
            keyboard: Keyboard.Numeric, maxLength: 6);
        if (string.IsNullOrWhiteSpace(code)) return false;
        await client.Auth.CompleteEmailOtpStepUpAsync(sent.VerificationUuid, code.Trim(), scope);
        return true;
    }
}
