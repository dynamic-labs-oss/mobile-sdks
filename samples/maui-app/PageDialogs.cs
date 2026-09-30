#if !NET10_0_OR_GREATER
namespace DynamicMauiDemo;

internal static class PageDialogs
{
    public static Task DisplayAlertAsync(this Page page, string title, string message, string cancel) =>
        page.DisplayAlert(title, message, cancel);

    public static Task<bool> DisplayAlertAsync(this Page page, string title, string message, string accept, string cancel) =>
        page.DisplayAlert(title, message, accept, cancel);

    public static Task<string> DisplayActionSheetAsync(this Page page, string title, string cancel, string? destruction, params string[] buttons) =>
        page.DisplayActionSheet(title, cancel, destruction, buttons);
}
#endif
