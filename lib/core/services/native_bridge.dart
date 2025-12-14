import 'dart:async';
import 'package:flutter/services.dart';
import 'package:wal/core/utils/debug_utils.dart';

class NativeBridgeService {
  static const _networkChannel = MethodChannel('com.stephen.wal/network_utils');
  static const _activityChannel = MethodChannel(
    'com.stephen.wal/activity_controls',
  );
  static const _wifiStateChannel = EventChannel('com.stephen.wal/wifi_state');

  // Lazy stream for Wi-Fi state changes
  Stream<Map<dynamic, dynamic>>? _wifiStateStream;

  /// Initialize Wi-Fi state listener (call once at app startup)
  Future<void> initializeWiFiStateListener() async {
    try {
      await _networkChannel.invokeMethod('initWiFiStateListener');
      dPrint('[NativeBridge] Wi-Fi state listener initialized');
    } on PlatformException catch (e) {
      dPrint('[NativeBridge] Error initializing Wi-Fi listener: ${e.message}');
    }
  }

  /// Get a stream of Wi-Fi connection state changes
  /// Emits a map with 'ssid' and 'state' keys whenever Wi-Fi connects/disconnects
  /// Automatically deduplicates consecutive identical events
  Stream<Map<dynamic, dynamic>> getWiFiStateStream() {
    _wifiStateStream ??= _wifiStateChannel
        .receiveBroadcastStream()
        .cast<Map>()
        .distinct((previous, next) {
          // Deduplicate if both SSID and state are identical
          return previous['ssid'] == next['ssid'] &&
              previous['state'] == next['state'];
        });
    return _wifiStateStream!;
  }

  /// Forces the app process to route traffic through the WiFi interface
  /// even if that WiFi has no internet access.
  Future<bool> bindProcessToWifi() async {
    try {
      final bool result = await _networkChannel.invokeMethod(
        'bindProcessToWifi',
      );
      return result;
    } on PlatformException catch (e) {
      dPrint("[NativeBridge] Error: ${e.message}");
      return false;
    }
  }

  /// Releases the network binding, returning to normal OS routing (4G, 5G, etc.).
  Future<bool> unbindProcess() async {
    try {
      final bool result = await _networkChannel.invokeMethod('unbindProcess');
      return result;
    } on PlatformException catch (e) {
      dPrint("[NativeBridge] Error: ${e.message}");
      return false;
    }
  }

  Future<void> minimizeApp() async {
    try {
      await _activityChannel.invokeMethod('minimizeApp');
    } catch (e) {
      dPrint("[NativeBridge] Error minimizing app: $e");
    }
  }

  Future<String> helloWorld() async {
    try {
      final String message = await _networkChannel.invokeMethod('helloWorld');
      return message;
    } on PlatformException catch (e) {
      dPrint("[NativeBridge] Error: ${e.message}");
      return "Error";
    }
  }

  /// Returns the SSID of the connected WiFi network, or null if not connected.
  Future<String?> getConnectedWifiSSID() async {
    try {
      final String? ssid = await _networkChannel.invokeMethod<String?>(
        'getConnectedWifiSSID',
      );
      return ssid;
    } on PlatformException catch (e) {
      dPrint('[NativeBridge] Error: ${e.message}');
      return null;
    }
  }
}
