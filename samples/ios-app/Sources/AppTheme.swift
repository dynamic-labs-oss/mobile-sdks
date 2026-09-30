import SwiftUI

/// Design tokens + button styles ported from the Dynamic Flutter SDK example
/// app (via examples/flutter-app/lib/style/*.dart, itself verified
/// bit-for-bit against flutter-sdk/example): same 7-color palette, same
/// 8-value spacing scale, radius-10 flat-bordered look. SwiftUI has no
/// app-wide "implicit style" mechanism as direct as Flutter's ThemeData or
/// MAUI's no-key Style — .buttonStyle(AppPrimaryButtonStyle())/
/// AppOutlinedButtonStyle()/AppPillButtonStyle() below are applied at each
/// call site instead, same shape as .buttonStyle(.borderedProminent)/
/// .bordered they replace.
///
/// Every color here is a LITERAL token — never a system-adaptive color
/// (`.secondary`, `Color(.secondarySystemBackground)`, `.red`, ...). The
/// reference forces light theme only (see DynamicDemoApp's
/// .preferredColorScheme(.light)); mixing in an adaptive color would diverge
/// from it on a dark-mode device. See DESIGN.md §1/§2.
enum AppColors {
    static let bgWhite = Color(red: 1.0, green: 1.0, blue: 1.0)
    static let bgBase4 = Color(red: 0xE7 / 255, green: 0xE8 / 255, blue: 0xED / 255)
    static let bgGrey = Color(red: 0xFA / 255, green: 0xFA / 255, blue: 0xFA / 255)
    static let txtPrimary = Color(red: 0x38 / 255, green: 0x3C / 255, blue: 0x48 / 255)
    static let txtSecondary = Color(red: 0x7B / 255, green: 0x7D / 255, blue: 0x86 / 255)
    static let txtError = Color(red: 1.0, green: 0x3B / 255, blue: 0x30 / 255)
    static let txtLink = Color(red: 0.0, green: 0x47 / 255, blue: 1.0)

    // Ad hoc colors (DESIGN.md §1) — used by name for specific states, never
    // re-derived from `.red`/`.gray`/etc.
    /// Destructive/delete buttons, error text on settings-style screens.
    static let red = Color(red: 0xF4 / 255, green: 0x43 / 255, blue: 0x36 / 255)
    /// Success text, "Delegated" status.
    static let green = Color(red: 0x4C / 255, green: 0xAF / 255, blue: 0x50 / 255)
    /// "Pending" status.
    static let orange = Color(red: 1.0, green: 0x98 / 255, blue: 0.0)
    /// List-item captions (id/date).
    static let grey600 = Color(red: 0x75 / 255, green: 0x75 / 255, blue: 0x75 / 255)
    /// QR code container border.
    static let grey300 = Color(red: 0xE0 / 255, green: 0xE0 / 255, blue: 0xE0 / 255)
    /// Secret/code chip background.
    static let grey200 = Color(red: 0xEE / 255, green: 0xEE / 255, blue: 0xEE / 255)
}

enum Spacing {
    static let space4: CGFloat = 4
    static let space6: CGFloat = 6
    static let space8: CGFloat = 8
    static let space12: CGFloat = 12
    static let space16: CGFloat = 16
    static let space20: CGFloat = 20
    static let space24: CGFloat = 24
    static let space32: CGFloat = 32
}

/// The type-token layer DESIGN.md §2 asks for: a real, centralized scale
/// every `Text` call site goes through — no bare `.font()`/`.tracking()`
/// literals scattered through screens. SwiftUI has no single "apply size +
/// weight + color + tracking" modifier, so each role below is a small
/// `View` extension instead of a `Font` value alone (a bare `Font` can't
/// carry `.tracking()` or `.foregroundColor()`).
///
/// Four named roles (DESIGN.md's 4-role scale) plus the two extra un-tokenized
/// sizes that recur across screens (section heading, list-item title).
extension Text {
    /// Screen headings, e.g. "Headless Login". 18/500/txtPrimary/-0.18.
    func appTitleMedium() -> some View {
        self.font(.system(size: 18, weight: .medium))
            .tracking(-0.18)
            .foregroundColor(AppColors.txtPrimary)
    }

    /// Button labels, emphasized body. 15/700/txtPrimary/-0.15.
    func appBodyLarge() -> some View {
        self.font(.system(size: 15, weight: .bold))
            .tracking(-0.15)
            .foregroundColor(AppColors.txtPrimary)
    }

    /// Default body text. 15/500/txtPrimary/-0.15.
    func appBodyMedium(color: Color = AppColors.txtPrimary) -> some View {
        self.font(.system(size: 15, weight: .medium))
            .tracking(-0.15)
            .foregroundColor(color)
    }

    /// Captions, hints, secondary labels. 12/500/0 tracking. Defaults to
    /// txtSecondary (its usual role as a caption/hint) — pass `.txtPrimary`
    /// to override per DESIGN.md's table.
    func appBodySmall(color: Color = AppColors.txtSecondary) -> some View {
        self.font(.system(size: 12, weight: .medium))
            .foregroundColor(color)
    }

    /// Section heading: 20, bold, default (un-themed) text color — DESIGN.md
    /// §2 explicitly leaves this one un-themed, not `txtPrimary`. With
    /// .preferredColorScheme(.light) forced at the root, the platform
    /// default resolves to a fixed color in both OS settings, so no
    /// adaptive-color leak — the fix here is the ABSENCE of a
    /// .foregroundColor override, not `.primary`.
    func appSectionHeading() -> some View {
        self.font(.system(size: 20, weight: .bold))
    }

    /// List-item title (settings-style rows: MFA devices, passkeys, business
    /// account members). 16, bold, txtPrimary.
    func appListItemTitle() -> some View {
        self.font(.system(size: 16, weight: .bold))
            .foregroundColor(AppColors.txtPrimary)
    }
}

/// Primary-action button: white background, dark text, 1pt bgBase4 border,
/// radius 10 — the one button look every button uses in the reference
/// (AppButtonStyle's shared _buttonStyle). Replaces .buttonStyle(.borderedProminent).
struct AppPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(AppColors.txtPrimary)
            .padding(13)
            .frame(maxWidth: .infinity)
            .background(AppColors.bgWhite)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
            .cornerRadius(10)
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Secondary-action button: same look as AppPrimaryButtonStyle but sized to
/// its content instead of filling the row — replaces .buttonStyle(.bordered).
/// In the reference, "Primary" and "Outlined" are the same visual style
/// with two semantic names (DESIGN.md §5) — this one just doesn't fill width.
/// `labelColor` defaults to `txtPrimary` but destructive actions (delete
/// passkey, remove business-account member, ...) pass `AppColors.red` per
/// DESIGN.md §7's "destructive ones tinted red" list-item-row rule — a
/// plain `.foregroundColor()` after `.buttonStyle()` has no effect here
/// since the style's own label already sets one.
struct AppOutlinedButtonStyle: ButtonStyle {
    var labelColor: Color = AppColors.txtPrimary

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(labelColor)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .background(AppColors.bgWhite)
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(AppColors.bgBase4, lineWidth: 1))
            .cornerRadius(10)
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Text/link button: transparent background, no border, txtLink label —
/// DESIGN.md §5 family 3.
struct AppTextButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(AppColors.txtLink)
            .padding(.horizontal, 13)
            .padding(.vertical, 8)
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

/// Filled/pill button — the fourth family DESIGN.md §5 calls out as
/// currently missing on every platform: solid txtLink background, white
/// label, fully rounded/capsule shape (not radius-10 — genuinely fully
/// rounded), elevation 0. Reserved for a screen's single primary CTA (e.g.
/// Create Wallet's submit button).
struct AppPillButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .bold))
            .foregroundColor(.white)
            .padding(.vertical, 13)
            .frame(maxWidth: .infinity)
            .background(AppColors.txtLink)
            .clipShape(Capsule())
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}
