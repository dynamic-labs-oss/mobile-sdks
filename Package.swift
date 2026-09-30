// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DynamicSDK",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "DynamicSDK", targets: ["DynamicSDKSupport"]),
        .library(name: "DynamicSdkBtc", targets: ["DynamicSdkBtcSupport"]),
        .library(name: "DynamicSdkBusinessAccount", targets: ["DynamicSdkBusinessAccountSupport"]),
        .library(name: "DynamicSdkEvm", targets: ["DynamicSdkEvmSupport"]),
        .library(name: "DynamicSdkEarn", targets: ["DynamicSdkEarnSupport"]),
        .library(name: "DynamicSdkExportPrivateKey", targets: ["DynamicSdkExportPrivateKeySupport"]),
        .library(name: "DynamicSdkExternalWallet", targets: ["DynamicSdkExternalWalletSupport"]),
        .library(name: "DynamicSdkExternalWalletCoinbase", targets: ["DynamicSdkExternalWalletCoinbaseSupport"]),
        .library(name: "DynamicSdkExternalWalletPhantom", targets: ["DynamicSdkExternalWalletPhantomSupport"]),
        .library(name: "DynamicSdkLegacyWalletUpgrade", targets: ["DynamicSdkLegacyWalletUpgradeSupport"]),
        .library(name: "DynamicSdkSolana", targets: ["DynamicSdkSolanaSupport"]),
        .library(name: "DynamicSdkStellar", targets: ["DynamicSdkStellarSupport"]),
        .library(name: "DynamicSdkSui", targets: ["DynamicSdkSuiSupport"]),
        .library(name: "DynamicSdkTon", targets: ["DynamicSdkTonSupport"]),
        .library(name: "DynamicSdkWaas", targets: ["DynamicSdkWaasSupport"]),
        .library(name: "DynamicSdkZerodev", targets: ["DynamicSdkZerodevSupport"]),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(name: "DynamicSdkCoreBinary", path: "swift/Frameworks/DynamicSdkCore.xcframework"),
        .binaryTarget(name: "DynamicSDKBinary", path: "swift/Frameworks/DynamicSDK.xcframework"),
        .binaryTarget(name: "DynamicSdkBtcBinary", path: "swift/Frameworks/DynamicSdkBtc.xcframework"),
        .binaryTarget(name: "DynamicSdkBusinessAccountBinary", path: "swift/Frameworks/DynamicSdkBusinessAccount.xcframework"),
        .binaryTarget(name: "DynamicSdkEvmBinary", path: "swift/Frameworks/DynamicSdkEvm.xcframework"),
        .binaryTarget(name: "DynamicSdkEarnBinary", path: "swift/Frameworks/DynamicSdkEarn.xcframework"),
        .binaryTarget(name: "DynamicSdkExportPrivateKeyBinary", path: "swift/Frameworks/DynamicSdkExportPrivateKey.xcframework"),
        .binaryTarget(name: "DynamicSdkExternalWalletBinary", path: "swift/Frameworks/DynamicSdkExternalWallet.xcframework"),
        .binaryTarget(name: "DynamicSdkExternalWalletCoinbaseBinary", path: "swift/Frameworks/DynamicSdkExternalWalletCoinbase.xcframework"),
        .binaryTarget(name: "DynamicSdkExternalWalletPhantomBinary", path: "swift/Frameworks/DynamicSdkExternalWalletPhantom.xcframework"),
        .binaryTarget(name: "DynamicSdkLegacyWalletUpgradeBinary", path: "swift/Frameworks/DynamicSdkLegacyWalletUpgrade.xcframework"),
        .binaryTarget(name: "DynamicSdkSolanaBinary", path: "swift/Frameworks/DynamicSdkSolana.xcframework"),
        .binaryTarget(name: "DynamicSdkStellarBinary", path: "swift/Frameworks/DynamicSdkStellar.xcframework"),
        .binaryTarget(name: "DynamicSdkSuiBinary", path: "swift/Frameworks/DynamicSdkSui.xcframework"),
        .binaryTarget(name: "DynamicSdkTonBinary", path: "swift/Frameworks/DynamicSdkTon.xcframework"),
        .binaryTarget(name: "DynamicSdkWaasBinary", path: "swift/Frameworks/DynamicSdkWaas.xcframework"),
        .binaryTarget(name: "DynamicSdkZerodevBinary", path: "swift/Frameworks/DynamicSdkZerodev.xcframework"),
        .binaryTarget(name: "SwiftBigIntBinary", path: "swift/Frameworks/SwiftBigInt.xcframework"),
        .target(
            name: "DynamicSDKSupport",
            dependencies: [
                .target(name: "DynamicSDKBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSDKSupport"
        ),
        .target(
            name: "DynamicSdkBtcSupport",
            dependencies: [
                .target(name: "DynamicSdkBtcBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkBtcSupport"
        ),
        .target(
            name: "DynamicSdkBusinessAccountSupport",
            dependencies: [
                .target(name: "DynamicSdkBusinessAccountBinary"),
                .target(name: "DynamicSdkCoreBinary"),
                .target(name: "DynamicSdkWaasBinary"),
            ],
            path: "swift/Sources/DynamicSdkBusinessAccountSupport"
        ),
        .target(
            name: "DynamicSdkEvmSupport",
            dependencies: [
                .target(name: "DynamicSdkEvmBinary"),
                .target(name: "DynamicSdkCoreBinary"),
                .target(name: "SwiftBigIntBinary"),
            ],
            path: "swift/Sources/DynamicSdkEvmSupport"
        ),
        .target(
            name: "DynamicSdkEarnSupport",
            dependencies: [
                .target(name: "DynamicSdkEarnBinary"),
                .target(name: "DynamicSdkCoreBinary"),
                .target(name: "DynamicSdkEvmBinary"),
                .target(name: "SwiftBigIntBinary"),
            ],
            path: "swift/Sources/DynamicSdkEarnSupport"
        ),
        .target(
            name: "DynamicSdkExportPrivateKeySupport",
            dependencies: [
                .target(name: "DynamicSdkExportPrivateKeyBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkExportPrivateKeySupport"
        ),
        .target(
            name: "DynamicSdkExternalWalletSupport",
            dependencies: [
                .target(name: "DynamicSdkExternalWalletBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkExternalWalletSupport"
        ),
        .target(
            name: "DynamicSdkExternalWalletCoinbaseSupport",
            dependencies: [
                .target(name: "DynamicSdkExternalWalletCoinbaseBinary"),
                .target(name: "DynamicSdkCoreBinary"),
                .target(name: "DynamicSdkExternalWalletBinary"),
            ],
            path: "swift/Sources/DynamicSdkExternalWalletCoinbaseSupport"
        ),
        .target(
            name: "DynamicSdkExternalWalletPhantomSupport",
            dependencies: [
                .target(name: "DynamicSdkExternalWalletPhantomBinary"),
                .target(name: "DynamicSdkCoreBinary"),
                .target(name: "DynamicSdkExternalWalletBinary"),
            ],
            path: "swift/Sources/DynamicSdkExternalWalletPhantomSupport"
        ),
        .target(
            name: "DynamicSdkLegacyWalletUpgradeSupport",
            dependencies: [
                .target(name: "DynamicSdkLegacyWalletUpgradeBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkLegacyWalletUpgradeSupport"
        ),
        .target(
            name: "DynamicSdkSolanaSupport",
            dependencies: [
                .target(name: "DynamicSdkSolanaBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkSolanaSupport"
        ),
        .target(
            name: "DynamicSdkStellarSupport",
            dependencies: [
                .target(name: "DynamicSdkStellarBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkStellarSupport"
        ),
        .target(
            name: "DynamicSdkSuiSupport",
            dependencies: [
                .target(name: "DynamicSdkSuiBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkSuiSupport"
        ),
        .target(
            name: "DynamicSdkTonSupport",
            dependencies: [
                .target(name: "DynamicSdkTonBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkTonSupport"
        ),
        .target(
            name: "DynamicSdkWaasSupport",
            dependencies: [
                .target(name: "DynamicSdkWaasBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkWaasSupport"
        ),
        .target(
            name: "DynamicSdkZerodevSupport",
            dependencies: [
                .target(name: "DynamicSdkZerodevBinary"),
                .target(name: "DynamicSdkCoreBinary"),
            ],
            path: "swift/Sources/DynamicSdkZerodevSupport"
        ),
    ]
)
