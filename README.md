# SpeedShare

Peer-to-peer file transfer over the local network. No cloud, no account, no upload — devices on the same Wi-Fi discover each other via UDP broadcast and stream files directly over TCP at line-rate.

## Components

- **`android/`** — Jetpack Compose + Material 3 app (`minSdk 24`, `targetSdk 36`).
- **`windows/`** — WPF (.NET 10) desktop app.
- **`windows-installer/`** — WPF setup bootstrapper that embeds and extracts the main app.
- **`protocol/PROTOCOL.md`** — Wire-protocol spec.
- **`windows.Tests/`** — Standalone test runner for the Windows models/services.

## Versioning

The single source of truth is `VERSION` at the repo root. Both the Android
`build.gradle.kts` (via `versionName`/`versionCode`) and the Windows
`Directory.Build.props` (via `Version`/`AssemblyVersion`/`FileVersion`) read it
at build time. The in-app version displayed in the UI and used by the
auto-update check is pulled from the build metadata, not hard-coded.

## Building

### Android

```pwsh
cd android
.\gradlew.bat :app:assembleDebug
```

Produces `android/app/build/outputs/apk/debug/app-debug.apk`.

### Windows

```pwsh
cd windows
dotnet build -c Release
dotnet publish -c Release -r win-x64 --self-contained false
```

Produces `windows/bin/Release/net10.0-windows/win-x64/publish/SpeedShareWindows.exe`.

### Windows installer

```pwsh
cd windows
dotnet publish -c Release -r win-x64 --self-contained false
cd ..\windows-installer
dotnet build -c Release
```

The installer embeds `..\windows\publish\SpeedShareWindows.exe`, so publish the
main app first.

## Tests

```pwsh
cd android
.\gradlew.bat :app:testDebugUnitTest

cd ..\windows.Tests
dotnet run -c Release
```

## License

See [LICENSE](LICENSE).
