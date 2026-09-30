# Dynamic Mobile SDKs

This repository distributes the public Dynamic mobile SDK binaries and sample applications.

All packages in one release use the same version. See `RELEASE-MANIFEST.json` for file sizes, SHA-256 digests, and the private source commit.

## Packages

- `swift/Frameworks` contains XCFrameworks for iOS devices and simulators.
- `kotlin/maven` contains the Android Maven repository and AAR packages.
- `dotnet/packages` contains the NuGet packages for .NET and MAUI.
- `artifacts` contains one ZIP archive for each native platform.
- `samples` contains Android, iOS, and MAUI applications that consume these binaries.

## Swift

Add this repository as a Swift Package Manager dependency. Select only the Dynamic products that your application uses.

```swift
.package(
    url: "https://github.com/dynamic-labs-oss/mobile-sdks.git",
    exact: "2.0.0-beta.1"
)
```

## Android

Download the Kotlin release archive or use the Maven repository in `kotlin/maven`. Each module publishes under the `xyz.dynamic` group.

```kotlin
dependencies {
    implementation("xyz.dynamic:dynamic-sdk:2.0.0-beta.1")
}
```

## .NET and MAUI

Download the .NET release archive or add `dotnet/packages` as a NuGet package source.

```xml
<PackageReference Include="Dynamic.Sdk" Version="2.0.0-beta.1" />
```

## Samples

The release pipeline builds each sample from the staged binaries before publication.

- `samples/android-app` uses `kotlin/maven`.
- `samples/ios-app` uses the binary package at the repository root.
- `samples/maui-app` uses `dotnet/packages`.

## License

The Dynamic Mobile SDKs use the MIT License. See `LICENSE`.
