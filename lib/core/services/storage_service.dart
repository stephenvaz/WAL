import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';
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

  Future<Directory> _resolveBackupDirectory() async {
    final downloadsDir = await getDownloadsDirectory();
    if (downloadsDir != null) return downloadsDir;
    return getApplicationDocumentsDirectory();
  }

  Future<File> backupWifiConfigs() async {
    final configs = await getAllConfigs();
    final payload = json.encode({
      'version': 1,
      'exportedAt': DateTime.now().toIso8601String(),
      'configs': configs.map((c) => c.toMap()).toList(),
    });

    final fileName =
        'wal_wifi_backup_${DateTime.now().millisecondsSinceEpoch}.json';

    // Prefer SAF save dialog on Android to place file in Downloads or user-selected
    // public storage without needing legacy storage permissions.
    if (Platform.isAndroid) {
      final savedPath = await FilePicker.platform.saveFile(
        dialogTitle: 'Save Wi-Fi backup',
        fileName: fileName,
        bytes: utf8.encode(payload),
        type: FileType.custom,
        allowedExtensions: const ['json'],
      );

      if (savedPath != null) {
        return File(savedPath);
      }
      // User cancelled; fall back to app directory to still provide a copy.
    }

    final directory = await _resolveBackupDirectory();
    if (!await directory.exists()) {
      await directory.create(recursive: true);
    }

    final file = File('${directory.path}/$fileName');
    await file.writeAsString(payload);
    return file;
  }

  Future<int> restoreWifiConfigs(File file) async {
    final raw = await file.readAsString();
    final decoded = json.decode(raw);

    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Invalid backup format');
    }

    final configsData = decoded['configs'];
    if (configsData is! List) {
      throw const FormatException('Invalid configs list');
    }

    int restored = 0;
    for (final item in configsData) {
      if (item is! Map) continue;
      try {
        final config = WifiConfig.fromMap(Map<String, dynamic>.from(item));
        if (config.ssid.isEmpty) continue;
        await saveConfig(config);
        restored++;
      } catch (_) {
        // Ignore invalid entries
      }
    }
    return restored;
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
