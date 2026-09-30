# Dynamic SDK binary samples

These apps consume Dynamic SDK 2.0.0-beta.1 from this repository.

- `android-app` reads the local Maven repository at `kotlin/maven`.
- `ios-app` reads the binary Swift package at the repository root.
- `maui-app` reads the local NuGet feed at `dotnet/packages`.

The apps install every public SDK module for their platform. Flutter is not part of this binary sample set.
