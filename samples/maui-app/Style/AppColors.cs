namespace DynamicMauiDemo;

/// <summary>Palette ported 1:1 from the Dynamic Flutter SDK example app
/// (examples/flutter-app/lib/style/app_colors.dart, itself verified
/// bit-for-bit against flutter-sdk/example) — same hex values, C# peer.</summary>
public static class AppColors
{
    public static readonly Color BgWhite = Color.FromArgb("#FFFFFF");
    public static readonly Color BgBase4 = Color.FromArgb("#E7E8ED");
    public static readonly Color BgGrey = Color.FromArgb("#FAFAFA");
    public static readonly Color TxtPrimary = Color.FromArgb("#383C48");
    public static readonly Color TxtSecondary = Color.FromArgb("#7B7D86");
    public static readonly Color TxtError = Color.FromArgb("#FF3B30");
    public static readonly Color TxtLink = Color.FromArgb("#0047FF");

    // Ad hoc state colors (DESIGN.md §1) — named exactly, never re-derived,
    // never substituted with Colors.Red/.secondary/etc.
    /// <summary>Destructive/delete actions, error text on settings-style screens.</summary>
    public static readonly Color Red = Color.FromArgb("#F44336");
    /// <summary>Success text, "Delegated" status.</summary>
    public static readonly Color Green = Color.FromArgb("#4CAF50");
    /// <summary>"Pending" status.</summary>
    public static readonly Color Orange = Color.FromArgb("#FF9800");
    /// <summary>List-item captions (id/date).</summary>
    public static readonly Color Grey600 = Color.FromArgb("#757575");
    /// <summary>QR code container border.</summary>
    public static readonly Color Grey300 = Color.FromArgb("#E0E0E0");
    /// <summary>Secret/code chip background (e.g. MFA recovery codes).</summary>
    public static readonly Color Grey200 = Color.FromArgb("#EEEEEE");
}
