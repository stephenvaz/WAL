import 'dart:async';
import 'dart:convert'; // Required for JSON
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:permission_handler/permission_handler.dart';
import 'native_bridge.dart';
import 'storage_service.dart';

class BackgroundServiceManager {
  static Future<void> initialize() async {
    final service = FlutterBackgroundService();

    const AndroidNotificationChannel channel = AndroidNotificationChannel(
      'auto_wifi_monitor',
      'Auto WiFi Monitor',
      description: 'Silent monitor for WiFi connections',
      importance: Importance.low,
    );

    final FlutterLocalNotificationsPlugin flutterLocalNotificationsPlugin =
        FlutterLocalNotificationsPlugin();

    await flutterLocalNotificationsPlugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(channel);

    await service.configure(
      androidConfiguration: AndroidConfiguration(
        onStart: onBackgroundStart,
        autoStart: true,
        isForegroundMode: true,
        notificationChannelId: 'auto_wifi_monitor',
        initialNotificationTitle: 'AutoWiFi Active',
        initialNotificationContent: 'Listening for WiFi connections',
        foregroundServiceNotificationId: 888,
        foregroundServiceTypes: [AndroidForegroundType.dataSync],
      ),
      iosConfiguration: IosConfiguration(
        autoStart: false,
        onForeground: onBackgroundStart,
      ),
    );
  }
}

@pragma('vm:entry-point')
void onBackgroundStart(ServiceInstance service) async {
  void dPrint(Object? object) {
    service.invoke('log', {'message': object});
  }

  final storage = StorageService();
  final nativeBridge = NativeBridgeService();
  service.updateNotification();

  // Initialize Wi-Fi state listener on native side
  await nativeBridge.initializeWiFiStateListener();

  // Listen for Wi-Fi state changes (connects/disconnects)
  nativeBridge.getWiFiStateStream().listen((wifiState) async {
    try {
      final String? ssid = wifiState['ssid'] as String?;
      final String? state = wifiState['state'] as String?;

      dPrint('[Background Service] Wi-Fi state changed: $ssid - $state');

      if (state != 'CONNECTED' || ssid == null) {
        dPrint('[Background Service] Wi-Fi disconnected or invalid state');
        return;
      }

      bool isLocationServiceEnabled =
          await Permission.locationAlways.serviceStatus.isEnabled;

      if (!isLocationServiceEnabled) {
        dPrint('[Background Service] Location Service is OFF');
        if (service is AndroidServiceInstance) {
          service.setForegroundNotificationInfo(
            title: 'AutoLogin Paused',
            content: 'Location (GPS) is disabled. Cannot scan WiFi.',
          );
        }
        return;
      }
      String cleanSSID = ssid.replaceAll('"', '');
      dPrint('[Background Service] Detected WiFi Connection: $cleanSSID');

      // Check if we have a stored config for this SSID
      String? jsonString = await storage.getRawData(cleanSSID);
      if (jsonString != null) {
        try {
          final Map<String, dynamic> configMap = jsonDecode(jsonString);
          bool isEnabled = configMap['isEnabled'] ?? true;
          if (isEnabled) {
            dPrint('[Background] Launching App to handle login...');
            service.invoke('trigger_login', {'config': jsonString});
          } else {
            dPrint('[Background Service] AutoLogin is disabled for $cleanSSID');
          }
        } catch (e) {
          dPrint('[Background Service] Error parsing JSON: $e');
        }
      } else {
        dPrint('[Background Service] No config found for SSID: $cleanSSID');
      }
    } catch (e) {
      dPrint('[Background Service] Error processing Wi-Fi state: $e');
    }
  });
}

extension BackgroundServiceExtension on ServiceInstance {
  void updateNotification() {
    on('update_notification').listen((event) {
      if (event != null) {
        String? title = event['title'];
        String? content = event['content'];

        // Explicitly check AND cast
        if (this is AndroidServiceInstance) {
          (this as AndroidServiceInstance).setForegroundNotificationInfo(
            title: title ?? "WAL - WiFi Auto Login",
            content: content ?? "Running...",
          );
        }
      }
    });
  }
}
