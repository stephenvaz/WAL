import 'dart:async';
import 'dart:convert'; // Required for JSON
import 'package:android_intent_plus/android_intent.dart';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
// import 'package:flutter_background_service_android/flutter_background_service_android.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:network_info_plus/network_info_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:wal/core/utils/debug_utils.dart';
import 'storage_service.dart';
import 'package:android_intent_plus/flag.dart';

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
        initialNotificationContent: 'Monitoring network...',
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
  final storage = StorageService(); // Ensure this service works in isolation

  service.updateNotification();

  Connectivity().onConnectivityChanged.listen((
    List<ConnectivityResult> results,
  ) async {
    dPrint("[Background Service] Connectivity Changed - $results");

    bool isLocationServiceEnabled =
        await Permission.location.serviceStatus.isEnabled;

    if (!isLocationServiceEnabled) {
      dPrint("[Background Service] Location Service is OFF");

      // Update Notification to warn user
      if (service is AndroidServiceInstance) {
        service.setForegroundNotificationInfo(
          title: "AutoLogin Paused",
          content: "Location (GPS) is disabled. Cannot scan WiFi.",
        );
      }
      return; // STOP HERE. Cannot get SSID without GPS on Android 8.1+
    }

    if (results.contains(ConnectivityResult.wifi)) {
      final info = NetworkInfo();
      String? wifiName = await info.getWifiName();

      if (wifiName != null) {
        String cleanSSID = wifiName.replaceAll('"', '');

        // Use the raw read because we are in a background isolate
        // and we need to manually parse the JSON here
        String? jsonString = await storage.getRawData(cleanSSID);
        dPrint("[Background Service] Detected WiFi Connection: $cleanSSID");
        if (jsonString != null) {
          try {
            final Map<String, dynamic> configMap = jsonDecode(jsonString);

            bool isEnabled = configMap['isEnabled'] ?? true;

            if (isEnabled) {
              // Pass the entire JSON object back to the main UI Isolate
              dPrint("[Background] Launching App to handle login...");
              
              // We use an Intent to bring the Activity to the Front
              final intent = AndroidIntent(
                action: 'android.intent.action.MAIN',
                category: 'android.intent.category.LAUNCHER',
                package: 'com.stephen.wal',
                componentName: 'com.stephen.wal.MainActivity',
                flags: <int>[
                  Flag.FLAG_ACTIVITY_NEW_TASK, // Mandatory for background launch
                  Flag.FLAG_ACTIVITY_REORDER_TO_FRONT, // Bring to top if already running
                  Flag.FLAG_ACTIVITY_SINGLE_TOP,
                ],
                // We pass the config as an "Extra" so the main app knows WHY it opened
                arguments: {'config': jsonString},
              );
              
              await intent.launch();
              service.invoke('trigger_login', {'config': jsonString});
            }
          } catch (e) {
            dPrint("[Background Service] Error parsing JSON: $e");
          }
        }
      }
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
