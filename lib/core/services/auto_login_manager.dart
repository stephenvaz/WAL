import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:wal/core/models/wifi_config.dart';
import 'package:wal/core/utils/debug_utils.dart';
import 'package:wal/features/autologin/logic/autologin_handler.dart';

class AutoLoginManager {
  // Singleton Pattern
  static final AutoLoginManager _instance = AutoLoginManager._internal();
  factory AutoLoginManager() => _instance;
  AutoLoginManager._internal();

  final AutoLoginHandler _handler = AutoLoginHandler();

  void initialize() {
    dPrint("[AutoLoginManager] Initialized and listening...");

    FlutterBackgroundService().on('trigger_login').listen((event) {
      if (event != null && event['config'] != null) {
        _processTrigger(event['config']);
      }
    });
  }

  Future<void> _processTrigger(String jsonString) async {
    try {
      dPrint("[AutoLoginManager] Login Sequence");
      final config = WifiConfig.fromJson(jsonString);
      await _handler.performAutoLogin(
        config: config,
        onStatusUpdate: (status) {
          dPrint("[AutoLoginManager] AutoLogin Status: $status");
        },
      );
    } catch (e) {
      dPrint("[AutoLoginManager] Error: $e");
    } finally {
      dPrint("[AutoLoginManager] Login Sequence Complete.");
    }
  }
}
