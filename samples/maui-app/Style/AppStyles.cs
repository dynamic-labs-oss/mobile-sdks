namespace DynamicMauiDemo;

/// <summary>
/// Global implicit styling ported from the Dynamic Flutter SDK example app's
/// theme.dart / app_button_style.dart / app_text_field_style.dart — the C#
/// peer, using MAUI's implicit (no x:Key) Style mechanism instead of
/// Flutter's ThemeData, so every existing page picks this up automatically
/// without being touched: grey page background, flat white bordered
/// cards/buttons (radius 10, no shadow), blue (#0047FF) accent. Applied once
/// in App.cs via Resources.Add(...) for every Style AppStyles.All() returns.
/// </summary>
public static class AppStyles
{
    public static IEnumerable<Style> All()
    {
        yield return new Style(typeof(ContentPage))
        {
            Setters = { new Setter { Property = Page.BackgroundColorProperty, Value = AppColors.BgGrey } },
        };

        yield return new Style(typeof(Label))
        {
            Setters =
            {
                new Setter { Property = Label.TextColorProperty, Value = AppColors.TxtPrimary },
                new Setter { Property = Label.FontSizeProperty, Value = 15.0 },
            },
        };

        // Mirrors AppButtonStyle's shared _buttonStyle — the outlined look
        // every button in the Flutter demo uses (a flat white box with a
        // light border), not Material's filled/elevated button. Kept as a
        // named property (not just yielded inline) so PillButton below can
        // BasedOn it instead of re-declaring every setter — an explicit
        // Style REPLACES the implicit one wholesale when applied, it
        // doesn't merge with it.
        yield return ButtonStyle;

        yield return new Style(typeof(Entry))
        {
            Setters =
            {
                new Setter { Property = Entry.BackgroundColorProperty, Value = AppColors.BgWhite },
                new Setter { Property = Entry.TextColorProperty, Value = AppColors.TxtPrimary },
                new Setter { Property = Entry.PlaceholderColorProperty, Value = AppColors.BgBase4 },
            },
        };

        yield return new Style(typeof(Editor))
        {
            Setters =
            {
                new Setter { Property = Editor.BackgroundColorProperty, Value = AppColors.BgWhite },
                new Setter { Property = Editor.TextColorProperty, Value = AppColors.TxtPrimary },
                new Setter { Property = Editor.PlaceholderColorProperty, Value = AppColors.BgBase4 },
            },
        };

        yield return new Style(typeof(Picker))
        {
            Setters =
            {
                new Setter { Property = Picker.BackgroundColorProperty, Value = AppColors.BgWhite },
                new Setter { Property = Picker.TextColorProperty, Value = AppColors.TxtPrimary },
                new Setter { Property = Picker.TitleColorProperty, Value = AppColors.TxtSecondary },
            },
        };

        yield return new Style(typeof(ActivityIndicator))
        {
            Setters = { new Setter { Property = ActivityIndicator.ColorProperty, Value = AppColors.TxtPrimary } },
        };

        // AppTheme's cardTheme peer — HomePage's per-wallet card is the one
        // Border in this app; its StrokeShape (corner radius) stays
        // per-instance since Border has no CornerRadius setter of its own.
        yield return new Style(typeof(Border))
        {
            Setters =
            {
                new Setter { Property = Border.BackgroundColorProperty, Value = AppColors.BgWhite },
                new Setter { Property = Border.StrokeProperty, Value = AppColors.BgBase4 },
            },
        };
    }

    /// <summary>Title-sized label (AppTextStyle.titleMedium's peer) —
    /// explicit, opt-in (MAUI's implicit-Style mechanism can't express
    /// Flutter's multiple named Label variants at once).</summary>
    public static Style TitleLabel { get; } = new Style(typeof(Label))
    {
        Setters =
        {
            new Setter { Property = Label.TextColorProperty, Value = AppColors.TxtPrimary },
            new Setter { Property = Label.FontSizeProperty, Value = 18.0 },
            new Setter { Property = Label.FontAttributesProperty, Value = FontAttributes.Bold },
        },
    };

    /// <summary>Error-colored label (AppTextStyle's txtError body text).</summary>
    public static Style ErrorLabel { get; } = new Style(typeof(Label))
    {
        Setters =
        {
            new Setter { Property = Label.TextColorProperty, Value = AppColors.TxtError },
            new Setter { Property = Label.FontSizeProperty, Value = 12.0 },
        },
    };

    /// <summary>Section heading (DESIGN.md §2: 20pt bold) — deliberately
    /// does NOT set TextColor. The reference leaves this one un-themed
    /// (falls through to the platform's default Label color), so this
    /// style matches that instead of "fixing" it into txtPrimary.</summary>
    public static Style SectionHeading { get; } = new Style(typeof(Label))
    {
        Setters =
        {
            new Setter { Property = Label.FontSizeProperty, Value = 20.0 },
            new Setter { Property = Label.FontAttributesProperty, Value = FontAttributes.Bold },
        },
    };

    /// <summary>List-item title (DESIGN.md §2: 16pt bold, txtPrimary) — used
    /// by every settings-style list row (passkeys, MFA devices, business
    /// account members/wallets/signers).</summary>
    public static Style ListItemTitle { get; } = new Style(typeof(Label))
    {
        Setters =
        {
            new Setter { Property = Label.TextColorProperty, Value = AppColors.TxtPrimary },
            new Setter { Property = Label.FontSizeProperty, Value = 16.0 },
            new Setter { Property = Label.FontAttributesProperty, Value = FontAttributes.Bold },
        },
    };

    /// <summary>Caption/hint label (DESIGN.md §2 bodySmall, txtSecondary
    /// override): 12pt, txtSecondary. Use this instead of a bare
    /// FontSize/TextColor pair — that's exactly the leak this style was
    /// added to close (WalletDetailsPage's two network-picker captions used
    /// Colors.Gray instead of a token).</summary>
    public static Style CaptionLabel { get; } = new Style(typeof(Label))
    {
        Setters =
        {
            new Setter { Property = Label.TextColorProperty, Value = AppColors.TxtSecondary },
            new Setter { Property = Label.FontSizeProperty, Value = 12.0 },
        },
    };

    /// <summary>The implicit outlined/white button look (AppButtonStyle's
    /// shared _buttonStyle) — factored out to a named property so
    /// <see cref="PillButton"/> can BasedOn it instead of duplicating every
    /// setter.</summary>
    public static Style ButtonStyle { get; } = new Style(typeof(Button))
    {
        Setters =
        {
            new Setter { Property = Button.BackgroundColorProperty, Value = AppColors.BgWhite },
            new Setter { Property = Button.TextColorProperty, Value = AppColors.TxtPrimary },
            new Setter { Property = Button.BorderColorProperty, Value = AppColors.BgBase4 },
            new Setter { Property = Button.BorderWidthProperty, Value = 1.0 },
            new Setter { Property = Button.CornerRadiusProperty, Value = 10 },
            new Setter { Property = Button.FontSizeProperty, Value = 14.0 },
            new Setter { Property = Button.FontAttributesProperty, Value = FontAttributes.Bold },
            new Setter { Property = Button.PaddingProperty, Value = new Thickness(13) },
        },
    };

    /// <summary>The fourth button family (DESIGN.md §5.4): solid txtLink
    /// background, white label, genuinely fully-rounded/stadium shape (a
    /// fixed HeightRequest + CornerRadius = height/2, not radius-10 — a
    /// corner radius alone can't make a pill out of MAUI's rectangular
    /// Button). Used sparingly, for the single primary CTA on a screen
    /// (e.g. Create Wallet's submit button) — every other button stays on
    /// the implicit outlined <see cref="ButtonStyle"/>. BasedOn ButtonStyle
    /// so it keeps the shared padding/font setters and only overrides what
    /// actually differs; an explicit Style applied via label.Style = ...
    /// replaces the implicit one wholesale, it does not merge with it,
    /// which is why this couldn't just be a few extra setters tacked onto
    /// the implicit Button style (MAUI has no per-instance variant knob).</summary>
    public static Style PillButton { get; } = new Style(typeof(Button))
    {
        BasedOn = ButtonStyle,
        Setters =
        {
            new Setter { Property = Button.BackgroundColorProperty, Value = AppColors.TxtLink },
            new Setter { Property = Button.TextColorProperty, Value = Colors.White },
            new Setter { Property = Button.BorderWidthProperty, Value = 0.0 },
            new Setter { Property = Button.CornerRadiusProperty, Value = 24 },
            new Setter { Property = Button.HeightRequestProperty, Value = 48.0 },
        },
    };

    /// <summary>Wraps <paramref name="root"/> in a NavigationPage with
    /// AppBarTheme's peer applied — every App.SetRoot(new NavigationPage(...))
    /// call site uses this instead of the bare constructor so the bar's
    /// look is consistent across every navigation swap, not just the first.</summary>
    public static NavigationPage Wrap(Page root) => new(root)
    {
        // NavigationPage has no separate icon-tint property (unlike Shell's
        // TitleColor/DisabledColor split), so this governs the title text
        // AND the back button — matches the reference's title text color
        // (txtPrimary) rather than its icon accent (txtLink); title text is
        // the more prominent element to get right without a way to split them.
        BarBackgroundColor = AppColors.BgWhite,
        BarTextColor = AppColors.TxtPrimary,
    };
}
