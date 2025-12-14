# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

WAL (WiFi Auto Login) is a Flutter Android application that automatically logs users into captive portal WiFi networks by:
1. Running a persistent background service that monitors WiFi connections
2. Detecting when the device connects to a configured WiFi network
3. Launching the app and using a headless WebView to automatically submit login credentials
4. Binding network traffic to WiFi (even without internet) to access the captive portal

The app is designed for Android 8.1+ and handles complex scenarios like screen-off detection and location service requirements for WiFi scanning.

## Development Commands

### Dependencies
```bash
# Install Flutter dependencies
flutter pub get

# Update dependencies
flutter pub upgrade
```

### Build & Run
```bash
# Run in debug mode (with debug prints enabled)
flutter run

# Run in release mode
flutter run --release

# Build APK
flutter build apk

# Build release APK
flutter build apk --release
```

### Testing
```bash
# Run all tests
flutter test

# Run a specific test file
flutter test test/widget_test.dart
```

### Code Quality
```bash
# Analyze code (lint checks)
flutter analyze

# Format code
flutter format .

# Format a specific file
flutter format lib/main.dart
```

### Clean Build
```bash
# Clean build artifacts (useful when encountering build issues)
flutter clean
flutter pub get
flutter run
```

## Architecture Overview

### Core Architecture Pattern
The app uses a **dual-isolate architecture** with platform channel bridging:

1. **Main UI Isolate** - Handles Flutter UI, navigation, and user interactions
2. **Background Service Isolate** - Runs independently via `flutter_background_service`, monitors WiFi connectivity
3. **Native Bridge (Kotlin)** - Provides platform-specific network binding via MethodChannel

### Critical Data Flow
```
WiFi Connection Event (Background Isolate)
  → Retrieves stored credentials from FlutterSecureStorage
  → Launches MainActivity via AndroidIntent (FLAG_ACTIVITY_REORDER_TO_FRONT)
  → Sends 'trigger_login' event to Main Isolate
  → AutoLoginManager receives event on Main Isolate
  → Navigates to LoginProcessingScreen (using GlobalKey<NavigatorState>)
  → AutoLoginHandler performs login:
      a) Binds process to WiFi network (via native Kotlin code)
      b) Launches HeadlessInAppWebView with portal URL
      c) Injects JavaScript to fill credentials and submit form
      d) Unbinds process network after completion
```

### Key Components

**Background Service** (`lib/core/services/background_service.dart`)
- Runs in a separate isolate with persistent foreground notification
- Listens to `Connectivity().onConnectivityChanged` stream
- Retrieves WiFi SSID and matches against stored configurations
- Uses `AndroidIntent` to bring app to foreground when match found
- Requires location services enabled (Android 8.1+ requirement for WiFi scanning)

**AutoLoginManager** (`lib/core/services/auto_login_manager.dart`)
- Singleton service that coordinates the auto-login process
- Owns a `GlobalKey<NavigatorState>` to enable navigation without BuildContext
- Listens for 'trigger_login' events from background service
- Prevents double execution with `isRunning` flag

**AutoLoginHandler** (`lib/features/autologin/logic/autologin_handler.dart`)
- Manages the headless WebView for captive portal interaction
- Uses `HeadlessInAppWebView` (runs without visible UI)
- Injects JavaScript to locate username/password fields and submit button
- Handles SSL certificate issues with `onReceivedServerTrustAuthRequest`

**NativeBridge** (`lib/core/services/native_bridge.dart`)
- Provides MethodChannel interface to Kotlin native code
- `bindProcessToWifi()` - Forces all app traffic through WiFi (even without internet)
- `unbindProcess()` - Restores normal network routing (cellular fallback)
- `minimizeApp()` - Sends app to background after successful login

**MainActivity.kt** (`android/app/src/main/kotlin/com/stephen/wal/MainActivity.kt`)
- Implements network binding using `ConnectivityManager.bindProcessToNetwork()`
- Sets `FLAG_SHOW_WHEN_LOCKED` and `FLAG_TURN_SCREEN_ON` for background launches
- Maintains `FLAG_KEEP_SCREEN_ON` during login process

**StorageService** (`lib/core/services/storage_service.dart`)
- Uses `flutter_secure_storage` for encrypted credential storage
- Stores `WifiConfig` objects keyed by SSID
- Provides both parsed (`WifiConfig`) and raw JSON access for background isolate

### Feature Organization
```
lib/features/autologin/
  ├── logic/autologin_handler.dart      # WebView automation logic
  └── presentation/
      ├── home_screen.dart              # Main UI with config list
      ├── config_sheet.dart             # Bottom sheet for adding/editing configs
      ├── login_processing_screen.dart  # Dark overlay during auto-login
      ├── setup_screen.dart             # Initial setup flow
      └── debug_screen.dart             # Debug logs viewer
```

## Important Technical Constraints

### Android Permissions & Requirements
- **Location Permission + GPS Enabled**: Required to get WiFi SSID on Android 8.1+
- **Notification Permission**: Required for foreground service
- **System Alert Window**: Allows app to show on lock screen
- All permissions checked on app launch in `home_screen.dart:_checkPermissions()`
- Service notification updates dynamically based on permission status

### Network Binding Behavior
- `bindProcessToNetwork()` is **Android API 23+ only**
- Falls back to deprecated `setProcessDefaultNetwork()` for older versions
- Binding persists until `unbindProcess()` is called
- Without binding, WebView will fail to reach captive portal on dual-connection devices (WiFi + cellular)

### JavaScript Injection Pattern
The app uses a generic injection strategy in `autologin_handler.dart:_injectCredentials()`:
```javascript
// Searches for common form field patterns
getElementById('username') || querySelector('input[name="username"]')
getElementById('password') || querySelector('input[name="password"]')
querySelector('button[type="submit"]') || querySelector('input[type="submit"]')
```
**When modifying for specific portals**: Edit this JavaScript template to target site-specific selectors.

### Background Service Isolate Limitations
- Cannot directly access Flutter widgets or BuildContext
- Must use raw JSON from `storage.getRawData()` instead of parsed models
- Communication with main isolate via `service.invoke()` events
- No direct UI manipulation capability

## Code Conventions

### Debug Logging
Use `dPrint()` from `lib/core/utils/debug_utils.dart` instead of raw `print()`:
```dart
import 'package:wal/core/utils/debug_utils.dart';

dPrint("AutoLogin Status: $status"); // Only prints in debug mode
```

### Credential Security
- Never log passwords or sensitive credentials
- Always use `flutter_secure_storage` for credential persistence
- Credentials passed between isolates should remain encrypted until use

### Navigation Pattern
- Use `AutoLoginManager().navigatorKey` for programmatic navigation without context
- Registered in `main.dart` as `navigatorKey: AutoLoginManager().navigatorKey`

### State Management
- Currently uses `StatefulWidget` with `setState()`
- No external state management library (Provider, Riverpod, etc.)
- Singleton pattern used for service classes

## Common Development Tasks

### Adding a New WiFi Configuration Field
1. Update `WifiConfig` model (`lib/core/models/wifi_config.dart`) with new field
2. Add field to `toMap()` and `fromMap()` methods
3. Update `ConfigSheet` UI (`lib/features/autologin/presentation/config_sheet.dart`)
4. Ensure background service can parse the updated JSON structure

### Customizing JavaScript Injection for Specific Portals
1. Edit `_injectCredentials()` in `lib/features/autologin/logic/autologin_handler.dart`
2. Use browser DevTools to inspect portal HTML structure
3. Add site-specific selectors or fallback logic
4. Test with `dPrint()` to verify page content after injection

### Testing Background Service
The background service only triggers on real WiFi connection changes:
1. Use `DebugScreen` to view logs
2. Test by connecting to an actual WiFi network with stored credentials
3. Check notification updates for permission status
4. Verify app launches from background when criteria met

### Updating Permissions
1. Add permission to `AndroidManifest.xml`
2. Add corresponding permission check in `home_screen.dart:_checkPermissions()`
3. Update notification status messages in same method
4. Consider whether permission needs `requestDialog: true` flow

## Package-Specific Notes

### flutter_inappwebview
- Version: ^6.1.5
- Used in headless mode only (no visible WebView UI)
- Set `javaScriptEnabled: true` and `mixedContentMode: MIXED_CONTENT_ALWAYS_ALLOW` for captive portals
- SSL errors handled with `onReceivedServerTrustAuthRequest`

### flutter_background_service
- Version: ^5.1.0
- Runs in separate isolate with different memory space
- Use `@pragma('vm:entry-point')` on `onBackgroundStart()` function
- Notification channel required for Android 8.0+

### flutter_secure_storage
- Version: ^10.0.0
- Works across isolates (both main and background service can access)
- Data encrypted at rest on device
- Keys are WiFi SSIDs (must be exact match, no quotes)

### connectivity_plus & network_info_plus
- `connectivity_plus`: Detects connection type changes (WiFi, cellular, none)
- `network_info_plus`: Retrieves WiFi SSID (requires location permission)
- SSID comes wrapped in quotes on some devices: `"NetworkName"` → use `.replaceAll('"', '')`
