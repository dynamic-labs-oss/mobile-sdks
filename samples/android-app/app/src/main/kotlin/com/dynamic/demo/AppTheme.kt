// Design tokens + Compose theme ported from the Dynamic Flutter SDK example
// app (via examples/flutter-app/lib/style/*.dart, itself verified bit-for-bit
// against flutter-sdk/example): same 7-color palette, same 8-value spacing
// scale, radius-10 flat-bordered look. Compose has no "implicit style"
// mechanism as direct as Flutter's ThemeData or MAUI's no-key Style, so the
// leverage here is narrower: MaterialTheme's ColorScheme/Shapes cover
// Card/OutlinedButton/TextField for free (their defaults read colorScheme.
// outline/onSurface/surface), but the FILLED Button used for primary actions
// (Send code/Verify/Create wallet/Sign/Send) needs an explicit wrapper
// (AppButton) since Material3's default filled button has no border and
// pulls from colorScheme.primary/onPrimary, not the white-bg/dark-text/
// bordered look every button uses in the reference.
package com.dynamic.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val BgWhite = Color(0xFFFFFFFF)
    val BgBase4 = Color(0xFFE7E8ED)
    val BgGrey = Color(0xFFFAFAFA)
    val TxtPrimary = Color(0xFF383C48)
    val TxtSecondary = Color(0xFF7B7D86)
    val TxtError = Color(0xFFFF3B30)
    val TxtLink = Color(0xFF0047FF)

    // Ad hoc colors used by name in the reference for specific states — see
    // DESIGN.md §1. Do NOT use Color.Gray/.secondary/etc for any of these.
    val Red = Color(0xFFF44336) // destructive/delete, error text on settings screens
    val Green = Color(0xFF4CAF50) // success / "Delegated"
    val Orange = Color(0xFFFF9800) // "Pending"
    val Grey600 = Color(0xFF757575) // list-item captions (id/date)
    val Grey300 = Color(0xFFE0E0E0) // QR code container border
    val Grey200 = Color(0xFFEEEEEE) // secret/code chip background
}

object Spacing {
    val Space4: Dp = 4.dp
    val Space6: Dp = 6.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space20: Dp = 20.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp
}

/**
 * DESIGN.md §2's 4-role type scale, wired as a real Compose [Typography] so
 * every `Text(...)` call site can go through `MaterialTheme.typography.*`
 * instead of a bare `fontSize =`. This was previously entirely missing (every
 * screen inlined `fontSize = 18.sp` etc) — the single biggest style gap
 * flagged for this platform.
 *
 * Only 4 of Material3's ~13 type roles are meaningfully used by the
 * reference; the rest keep Compose's stock defaults (never rendered by this
 * app) rather than being force-fit into a role they don't semantically match.
 */
val AppTypography = Typography(
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TxtPrimary,
        letterSpacing = (-0.18).sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.TxtPrimary,
        letterSpacing = (-0.15).sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TxtPrimary,
        letterSpacing = (-0.15).sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = AppColors.TxtPrimary,
        letterSpacing = TextUnit.Unspecified,
    ),
    // Material3's Button/OutlinedButton/TextButton/DropdownMenuItem all
    // resolve their label through typography.labelLarge — left at Compose's
    // baseline (14sp/Medium), EVERY button not explicitly styled with
    // AppTextStyles.ButtonLabel would render at the wrong weight, which was
    // most of them (every OutlinedButton/TextButton across every screen).
    // Pinning this role to DESIGN.md §5's 14/700 button-label spec fixes all
    // of those call sites for free. Deliberately NO color here (unlike the
    // other 4 roles) — ButtonDefaults' own contentColor must keep flowing
    // through, or AppPillButton's white label and every destructive red
    // TextButton/OutlinedButton (Red content color) would get overridden
    // back to txtPrimary.
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.15).sp,
    ),
)

/**
 * The two un-tokenized sizes DESIGN.md §2 calls out separately (they appear
 * directly in the reference, outside the 4-role scale, but recur often
 * enough on this platform too to deserve a named constant instead of being
 * re-inlined at every call site).
 */
object AppTextStyles {
    /** Section heading (e.g. "Wallets", each secondary screen's own in-body
     * heading next to "← Back"). Deliberately NOT `txtPrimary` — the
     * reference leaves this one un-themed (default text color); match that,
     * don't "fix" it. */
    val SectionHeading = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)

    /** List-item title (wallet card address line, passkey/MFA-device rows, business-account members). */
    val ListItemTitle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TxtPrimary)

    /** Button label — the one place the reference diverges from the 15pt body
     * scale (14/700 instead of 15/700). Applied by [AppButton]/[AppPillButton]
     * internally; exposed here too for any button-styled Text built by hand. */
    val ButtonLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TxtPrimary)
}

// AppColorScheme derivation: DESIGN.md asks for either (a) a real HCT
// tonal-palette derivation from the #0047FF seed (ColorScheme.fromSeed's
// Flutter equivalent), or (b) explicitly pinning the extra roles unthemed M3
// widgets actually read, whichever is more robust in this Compose version.
// Chosen: (b). androidx.compose.material3:material3 in this BOM
// (2024.09.03) only ships a WALLPAPER-based dynamic scheme
// (dynamicLightColorScheme(context), Android 12+, ignores our seed entirely)
// — there is no public "generate a ColorScheme from an arbitrary seed color"
// API in this artifact; that requires pulling in Google's separate
// material-color-utilities (or an unofficial wrapper), a new dependency this
// repo's Zscaler-blocked/JFrog-mirrored Gradle setup has no verified
// resolution path for yet. Pinning the roles Material3's own unthemed
// widgets (AlertDialog, Snackbar, DropdownMenu, PullToRefresh) read is the
// robust option available today with zero new dependencies; approximated by
// hand from the seed (light "container" tint / dark "on-container" ink)
// rather than a true HCT tone-40/90/etc computation.
private val AppColorScheme = lightColorScheme(
    primary = AppColors.TxtLink,
    onPrimary = AppColors.BgWhite,
    // Approximate HCT tone~90 (light container) / tone~10 (dark on-container)
    // for the seed #0047FF — read by unthemed FilledIconButton/Chip/Switch-on-track etc.
    primaryContainer = Color(0xFFD9E1FF),
    onPrimaryContainer = Color(0xFF001947),
    secondary = AppColors.TxtLink,
    onSecondary = AppColors.BgWhite,
    secondaryContainer = Color(0xFFD9E1FF),
    onSecondaryContainer = Color(0xFF001947),
    tertiary = AppColors.TxtLink,
    onTertiary = AppColors.BgWhite,
    background = AppColors.BgGrey,
    surface = AppColors.BgWhite,
    surfaceVariant = AppColors.BgGrey,
    onBackground = AppColors.TxtPrimary,
    onSurface = AppColors.TxtPrimary,
    onSurfaceVariant = AppColors.TxtSecondary,
    // OutlinedButton's default border + Card's default outline both read
    // colorScheme.outline — this alone gets both looking right without
    // touching every OutlinedButton/Card call site.
    outline = AppColors.BgBase4,
    outlineVariant = AppColors.BgBase4,
    // Snackbar's own background and text: it reads inverseSurface /
    // inverseOnSurface, and leaving them unset left it on Material's
    // baseline pair, which against this scheme came out unreadable (dark box,
    // oddly tinted text). Pinned to a plain dark-on-light-ink pair.
    inverseSurface = AppColors.TxtPrimary,
    inverseOnSurface = AppColors.BgWhite,
    // Read by DropdownMenu/Dialog surface tinting and pull-to-refresh's
    // indicator — pin to the seed so those unthemed widgets don't default to
    // Material's baseline purple.
    surfaceTint = AppColors.TxtLink,
    inversePrimary = AppColors.TxtLink,
    error = AppColors.TxtError,
    onError = AppColors.BgWhite,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun DynamicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/**
 * Primary-action button: white background, dark text, 1dp bgBase4 border,
 * radius 10 — the one button look every button uses in the reference
 * (AppButtonStyle's shared _buttonStyle), used here in place of Material3's
 * default filled Button (blue bg, no border) for every primary action
 * (Send code / Verify / Create wallet / Sign / Send transaction / Reveal
 * Private Key).
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.BgWhite,
            contentColor = AppColors.TxtPrimary,
            disabledContainerColor = AppColors.BgWhite,
            disabledContentColor = AppColors.BgBase4,
        ),
        border = BorderStroke(1.dp, AppColors.BgBase4),
        content = content,
    )
}

/**
 * Filled/pill button — DESIGN.md §5's 4th button family, previously missing
 * entirely on Android (AppButton unconditionally forced every button to the
 * white/bordered look). Solid `txtLink` background, white label, fully
 * rounded/stadium shape (CircleShape on a Button sizes the corner radius to
 * half the button's own height, i.e. genuinely stadium-shaped, not radius-10
 * like every other button family). Used ONLY for a screen's single primary
 * CTA — the Create Wallet screen's submit button is the reference's
 * `FilledButton.icon` moment this ports.
 */
@Composable
fun AppPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.TxtLink,
            contentColor = AppColors.BgWhite,
            disabledContainerColor = AppColors.BgBase4,
            disabledContentColor = AppColors.BgWhite,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 13.dp),
        content = content,
    )
}
