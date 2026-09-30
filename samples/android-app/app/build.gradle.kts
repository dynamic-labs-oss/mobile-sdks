plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dynamic.demo"
    // android-35 is installed locally; do not bump without installing the platform.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dynamic.demo"
        // 23+ for the Keystore-backed Tink master key (DynamicSecureStorage).
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // web3j uses java.time; desugaring back-ports it below API 26 so we keep
        // minSdk 23 (required by DynamicSecureStorage's Keystore master key).
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // web3j (and its transitive deps) ship overlapping META-INF entries that
    // otherwise trip the packager.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/DISCLAIMER",
                "META-INF/*LICENSE*",
                "META-INF/*NOTICE*",
                "META-INF/*.txt",
                "META-INF/*.md",
                "META-INF/INDEX.LIST",
                "META-INF/versions/**",
            )
        }
    }

}

dependencies {
    // The demo installs every public SDK module.
    implementation("xyz.dynamic:dynamic-sdk:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-evm:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-solana:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-btc:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-sui:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-business-account:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-earn:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-stellar:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-ton:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-zerodev:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-legacy-wallet-upgrade:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-waas:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-export-private-key:2.0.0-beta.1")
    // Opt-in external wallets (MetaMask, ...) over native WalletConnect.
    implementation("xyz.dynamic:dynamic-sdk-external-wallet:2.0.0-beta.1")
    // Phantom, which has no WalletConnect namespace — its own module because
    // it brings libsodium.
    implementation("xyz.dynamic:dynamic-sdk-external-wallet-phantom:2.0.0-beta.1")
    implementation("xyz.dynamic:dynamic-sdk-external-wallet-coinbase:2.0.0-beta.1")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}

// Two extensions disagree about OkHttp, and the loser crashes the app:
//
//  - kotlin-solana pulls com.solanamobile:rpc-okiodriver, which asks for
//    okhttp 5.0.0-alpha.12;
//  - kotlin-external-wallet pulls reown, whose own BOM asks for 4.12.0.
//
// Gradle picks the highest, so everything ran on the 5.0.0 alpha — and
// reown's relay client installs beagle's OkHttp logging interceptor, which
// calls ResponseBody.string() on the WebSocket upgrade response. OkHttp 5
// makes that body deliberately unreadable and throws IllegalStateException,
// on OkHttp's own dispatcher thread, so the process dies the moment a wallet
// is selected. Measured on a Pixel 8a, not theoretical.
//
// 4.12.0 is what reown is built against and what the Solana driver works on,
// so the conflict is resolved there. A customer combining useSolana() with
// useExternalWallet() needs the same line until reown moves off beagle.
configurations.all {
    resolutionStrategy {
        force("com.squareup.okhttp3:okhttp:4.12.0")
    }
}
