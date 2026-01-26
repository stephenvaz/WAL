import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:wal/core/models/wifi_config.dart';
import 'package:wal/core/services/storage_service.dart';
import 'package:wal/core/utils/debug_utils.dart';
import 'package:wal/features/autologin/logic/autologin_handler.dart';

class AutoLoginManager {
  // Singleton Pattern
  static final AutoLoginManager _instance = AutoLoginManager._internal();
  factory AutoLoginManager() => _instance;
  AutoLoginManager._internal();

  final AutoLoginHandler _handler = AutoLoginHandler();
  final StorageService _storage = StorageService();
  final FlutterLocalNotificationsPlugin _notifications =
      FlutterLocalNotificationsPlugin();
  bool _notificationsInitialized = false;

  static const String _resultChannelId = 'auto_login_result';
  static const String _retryActionId = 'retry_login';

  Future<void> initialize() async {
    dPrint("[AutoLoginManager] Initialized and listening...");

    await _initializeNotifications();

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
      await _processConfig(config, source: 'trigger');
    } catch (e) {
      dPrint("[AutoLoginManager] Error: $e");
    }
  }

  Future<void> _processConfig(
    WifiConfig config, {
    required String source,
  }) async {
    final success = await _handler.performAutoLogin(
      config: config,
      onStatusUpdate: (status) {
        dPrint("[AutoLoginManager] AutoLogin Status: $status");
      },
    );

    await _showResultNotification(config: config, success: success);
  }

  Future<void> _initializeNotifications() async {
    if (_notificationsInitialized) return;

    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidInit);

    await _notifications.initialize(
      initSettings,
      onDidReceiveNotificationResponse: handleNotificationResponse,
      onDidReceiveBackgroundNotificationResponse: notificationTapBackground,
    );

    const AndroidNotificationChannel channel = AndroidNotificationChannel(
      _resultChannelId,
      'AutoLogin Results',
      description: 'Success/failure notifications for WiFi login attempts',
      importance: Importance.high,
    );

    await _notifications
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(channel);

    _notificationsInitialized = true;
  }

  Future<void> handleNotificationResponse(NotificationResponse response) async {
    final payload = response.payload;
    if (response.id != null) {
      await _notifications.cancel(response.id!);
    }

    if (response.actionId == _retryActionId && payload != null) {
      await _retryLoginForSsid(payload);
    }
  }

  Future<void> _retryLoginForSsid(String ssid) async {
    final config = await _storage.getConfig(ssid);
    if (config == null) {
      await _showResultNotification(
        config: WifiConfig(ssid: ssid, url: ''),
        success: false,
        message: 'No config found for $ssid',
      );
      return;
    }

    if (!config.isEnabled) {
      await _showResultNotification(
        config: config,
        success: false,
        message: 'AutoLogin is disabled for ${config.ssid}',
      );
      return;
    }

    await _processConfig(config, source: 'retry');
  }

  int _notificationIdForSsid(String ssid) {
    return ssid.hashCode & 0x7fffffff;
  }

  Future<void> _showResultNotification({
    required WifiConfig config,
    required bool success,
    String? message,
  }) async {
    final title = success ? 'WiFi Login Success' : 'WiFi Login Failed';
    final body =
        message ??
        (success
            ? 'Connected on ${config.ssid}.'
            : 'Unable to login on ${config.ssid}. Tap Retry.');

    final actions = success
        ? <AndroidNotificationAction>[]
        : <AndroidNotificationAction>[
            const AndroidNotificationAction(
              _retryActionId,
              'Retry',
              cancelNotification: true,
            ),
          ];

    final details = NotificationDetails(
      android: AndroidNotificationDetails(
        _resultChannelId,
        'AutoLogin Results',
        channelDescription:
            'Success/failure notifications for WiFi login attempts',
        importance: Importance.high,
        priority: Priority.high,
        actions: actions,
      ),
    );

    await _notifications.show(
      _notificationIdForSsid(config.ssid),
      title,
      body,
      details,
      payload: config.ssid,
    );
  }
}

@pragma('vm:entry-point')
void notificationTapBackground(NotificationResponse response) {
  AutoLoginManager().handleNotificationResponse(response);
}
