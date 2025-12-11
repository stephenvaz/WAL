import 'package:flutter/services.dart';
import 'package:wal/core/utils/debug_utils.dart';

class NativeBridgeService {
  static const _platform = MethodChannel('com.stephen.wal/network_utils');

  /// Forces the app process to route traffic through the WiFi interface
  /// even if that WiFi has no internet access.
  Future<bool> bindProcessToWifi() async {
    try {
      final bool result = await _platform.invokeMethod('bindProcessToWifi');
      return result;
    } on PlatformException catch (e) {
      dPrint("NativeBridge Error: ${e.message}");
      return false;
    }
  }

  /// Releases the network binding, returning to normal OS routing (4G, 5G, etc.).
  Future<bool> unbindProcess() async {
    try {
      final bool result = await _platform.invokeMethod('unbindProcess');
      return result;
    } on PlatformException catch (e) {
      dPrint("NativeBridge Error: ${e.message}");
      return false;
    }
  }

  Future<void> minimizeApp() async {
    try {
      await _platform.invokeMethod('minimizeApp');
    } catch (e) {
      dPrint("Error minimizing app: $e");
    }
  }
}