import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:wal/core/models/wifi_config.dart';

class StorageService {
  static const _storage = FlutterSecureStorage();

  // Save (Create or Update)
  Future<void> saveConfig(WifiConfig config) async {
    await _storage.write(key: config.ssid, value: config.toJson());
  }

  // Add this to StorageService class
  Future<String?> getRawData(String key) async {
    return await _storage.read(key: key);
  }

  // Get Single
  Future<WifiConfig?> getConfig(String ssid) async {
    String? data = await _storage.read(key: ssid);
    if (data == null) return null;
    try {
      return WifiConfig.fromJson(data);
    } catch (e) {
      return null;
    }
  }

  // Get All (For List View)
  Future<List<WifiConfig>> getAllConfigs() async {
    Map<String, String> allData = await _storage.readAll();
    List<WifiConfig> configs = [];

    allData.forEach((key, value) {
      try {
        configs.add(WifiConfig.fromJson(value));
      } catch (e) {
        // Handle legacy data or corruption if needed
      }
    });
    return configs;
  }

  // Delete
  Future<void> deleteConfig(String ssid) async {
    await _storage.delete(key: ssid);
  }
}

enum FeatureFlags {
  checkConnectivityBeforeLogin,
  debugLogsEnabled;

  static const _storage = FlutterSecureStorage();

  Future<void> setFeatureFlag(bool isEnabled) async {
    await _storage.write(key: toString(), value: isEnabled ? 'true' : 'false');
  }

  Future<bool> isEnabled() async {
    String? value = await _storage.read(key: toString());
    return value == 'true';
  }
}
