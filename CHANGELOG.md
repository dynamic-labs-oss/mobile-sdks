# Changelog

## 2.0.0-beta.2

### Features

- Add business account signing proposal support ([#252](https://github.com/dynamic-labs/mobile-sdks/pull/252))
- Add standalone policy engine packages ([#253](https://github.com/dynamic-labs/mobile-sdks/pull/253))
- Migrate Sui mobile SDKs to gRPC ([#254](https://github.com/dynamic-labs/mobile-sdks/pull/254))
- align mobile instrumentation with the JS SDK ([#258](https://github.com/dynamic-labs/mobile-sdks/pull/258))
- align policy engine and business accounts with the JS SDK ([#261](https://github.com/dynamic-labs/mobile-sdks/pull/261))
- add customRole to business-account members ([#263](https://github.com/dynamic-labs/mobile-sdks/pull/263))
- **business-account:** accept initialSignerRules on addBusinessAccountSigner ([#267](https://github.com/dynamic-labs/mobile-sdks/pull/267))
- **business-account:** throw typed named errors from resumeSigningProposal ([#275](https://github.com/dynamic-labs/mobile-sdks/pull/275))
- **business-account:** validate SVM signing intents and stricter intent checks ([#276](https://github.com/dynamic-labs/mobile-sdks/pull/276))
- **waas:** add resume7702AuthorizationProposal ([#278](https://github.com/dynamic-labs/mobile-sdks/pull/278))
- **waas:** add prepareSigningIntent and prepareExportIntent to WaasEngine ([#279](https://github.com/dynamic-labs/mobile-sdks/pull/279))
- **auth:** log and rethrow ProjectSettings parse failures in initialize ([#280](https://github.com/dynamic-labs/mobile-sdks/pull/280))
- **btc:** add a public buildPsbt generated from the spec ([#287](https://github.com/dynamic-labs/mobile-sdks/pull/287))
- **waas:** add signRawMessage for raw 32-byte digests, as dynamic-js-sdk does ([#289](https://github.com/dynamic-labs/mobile-sdks/pull/289))
- **evm:** add calculateEvmTransactionFee, as dynamic-js-sdk does ([#290](https://github.com/dynamic-labs/mobile-sdks/pull/290))
- **auth:** add getDeviceRegistrationTokenFromUrl and detectDeviceRegistrationRedirect, as dynamic-js-sdk does ([#292](https://github.com/dynamic-labs/mobile-sdks/pull/292))
- **waas:** add rawSign and password to signRawMessage, as dynamic-js-sdk does ([#293](https://github.com/dynamic-labs/mobile-sdks/pull/293))
- **evm:** add humanReadableAmount and usdAmount to calculateEvmTransactionFee, as dynamic-js-sdk does ([#294](https://github.com/dynamic-labs/mobile-sdks/pull/294))

### Fixes

- **auth:** tear down the WaaS engine on sign-out ([#255](https://github.com/dynamic-labs/mobile-sdks/pull/255))
- **dart:** add policy engine package license ([#256](https://github.com/dynamic-labs/mobile-sdks/pull/256))
- **release:** add DynamicSdkPolicyEngine to the Swift dependency map ([#260](https://github.com/dynamic-labs/mobile-sdks/pull/260))
- model policy layer deletion markers and import launch in Kotlin ([#262](https://github.com/dynamic-labs/mobile-sdks/pull/262))
- **deps:** bump brace-expansion to 5.0.12 via pnpm override ([#265](https://github.com/dynamic-labs/mobile-sdks/pull/265))
- **deps:** bump turbo to 2.11.7 + override esbuild \>=0.25.0 (medium severity alerts) ([#266](https://github.com/dynamic-labs/mobile-sdks/pull/266))
- **business-account:** default thresholdSignatureScheme to TWO\_OF\_TWO when creating a wallet ([#268](https://github.com/dynamic-labs/mobile-sdks/pull/268))
- **business-account:** resume message and raw signing proposals on any chain ([#269](https://github.com/dynamic-labs/mobile-sdks/pull/269))
- **generator:** make generated Dart clean under flutter\_lints ([#277](https://github.com/dynamic-labs/mobile-sdks/pull/277))
- **swift:** align Swift manifests and declare the dependencies each package uses ([#283](https://github.com/dynamic-labs/mobile-sdks/pull/283))
- **swift:** build each XCFramework from its own package so Core is shared ([#284](https://github.com/dynamic-labs/mobile-sdks/pull/284))
- **swift:** keep Sui gRPC types and imports out of the public interface ([#285](https://github.com/dynamic-labs/mobile-sdks/pull/285))
- **swift:** give each framework its own Sodium module name ([#286](https://github.com/dynamic-labs/mobile-sdks/pull/286))
- **flutter:** make the Flutter demo analyze cleanly against the split packages ([#288](https://github.com/dynamic-labs/mobile-sdks/pull/288))
- **swift:** stop Evm embedding swift-nio and swift-log by repinning web3.swift ([#291](https://github.com/dynamic-labs/mobile-sdks/pull/291))
- **ci:** repair release workflow and Kotlin/.NET package builds ([#297](https://github.com/dynamic-labs/mobile-sdks/pull/297))
- **deps:** bump swift-nio to 2.103.0 + tsx to 4.23.15 (Dependabot highs) ([#298](https://github.com/dynamic-labs/mobile-sdks/pull/298))
- **dotnet:** commit the MAUI targets file and resolve Android bridge plugins in CI ([#300](https://github.com/dynamic-labs/mobile-sdks/pull/300))

## 2.0.0-beta.1

- Add generated Dynamic SDKs for Dart, Kotlin, Swift, and .NET.
- Add authentication, MFA, and Business Account APIs.
- Add opt-in EVM, Solana, TON, Bitcoin, Stellar, Sui, Earn, and ZeroDev modules.
- Add external wallet, embedded wallet, and private key export modules.
- Add SDK instrumentation and platform request metadata.
- Add OpenAPI drift checks and cross-platform release tests.
