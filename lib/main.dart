import 'package:flutter/material.dart';
import 'package:wal/core/services/auto_login_manager.dart';
import 'package:wal/core/utils/log_service.dart';
import 'package:wal/features/autologin/presentation/home_screen.dart';
import 'core/services/background_service.dart';
import 'core/constants/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await LogService().initialize();
  await AutoLoginManager().initialize();
  BackgroundServiceManager.initialize();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WAL - WiFi Auto Login',
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      home: const HomeScreen(),
    );
  }
}

// lib/
// ├── core/
// │   ├── constants/
// │   │   └── app_theme.dart          # Centralized Theme Data
// │   └── services/
// │       ├── background_service.dart # Handles background execution (Isolate 2)
// │       ├── native_bridge.dart      # Handles MethodChannel calls (Kotlin)
// │       └── storage_service.dart    # Handles Secure Storage
// ├── features/
// │   └── autologin/
// │       ├── logic/
// │       │   └── autologin_handler.dart # Manages Headless WebView & Injection
// │       └── presentation/
// │           └── setup_screen.dart      # The UI for entering credentials
// └── main.dart                          # Entry point
