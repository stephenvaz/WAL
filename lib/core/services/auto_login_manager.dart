import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:wal/core/models/wifi_config.dart';
import 'package:wal/core/utils/debug_utils.dart';
import 'package:wal/features/autologin/logic/autologin_handler.dart';
import 'package:flutter/material.dart';
import 'package:wal/features/autologin/presentation/login_processing_screen.dart';

class AutoLoginManager {
  // Singleton Pattern
  static final AutoLoginManager _instance = AutoLoginManager._internal();
  factory AutoLoginManager() => _instance;
  AutoLoginManager._internal();

  final AutoLoginHandler _handler = AutoLoginHandler();
  
  // GLOBAL KEY: Allows navigation without a BuildContext
  final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  // Flag to prevent double execution
  bool isRunning = false;

  void initialize() {
    dPrint("AutoLoginManager: Initialized and listening...");
    
    FlutterBackgroundService().on('trigger_login').listen((event) {
      if (event != null && event['config'] != null) {
        _processTrigger(event['config']);
      }
    });
  }

  Future<void> _processTrigger(String jsonString) async {
    if (isRunning) return; 
    isRunning = true;

    try {
      dPrint("AutoLoginManager: Starting Wake-Up Sequence");
      
      final config = WifiConfig.fromJson(jsonString);

      // 1. NAVIGATE TO PROCESSING SCREEN
      // Push the dark "Logging In" screen over whatever was there
      navigatorKey.currentState?.push(
        MaterialPageRoute(builder: (_) => const LoginProcessingScreen()),
      );

      // 2. EXECUTE LOGIC
      await _handler.performAutoLogin(
        url: config.url,
        username: config.username,
        password: config.password,
        onStatusUpdate: (status) {
          dPrint("AutoLogin Status: $status");
          // Ideally, use a ValueNotifier here to update the text on the screen
        },
      );

    } catch (e) {
      dPrint("AutoLoginManager Error: $e");
    } finally {
      isRunning = false;
      
      // Cleanup: If the app didn't minimize (or error occurred),
      // pop the loading screen so the user lands back on Home.
      if (navigatorKey.currentState?.canPop() ?? false) {
         navigatorKey.currentState?.pop();
      }
    }
  }
}