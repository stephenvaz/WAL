import 'dart:async';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:wal/core/services/storage_service.dart';
import 'package:wal/core/utils/debug_utils.dart';

class LogEntry {
  final DateTime timestamp;
  final String message;
  LogEntry(this.timestamp, this.message);

  @override
  String toString() =>
      "[${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}:${timestamp.second.toString().padLeft(2, '0')}] $message";
}

class LogService {
  static final LogService _instance = LogService._internal();
  factory LogService() => _instance;
  LogService._internal();

  static const _keyBuffer = 'debug_logs_buffer';
  static const _storage = FlutterSecureStorage();

  final _controller = StreamController<List<LogEntry>>.broadcast();
  List<LogEntry> _buffer = [];
  bool _enabled = false;
  bool _initialized = false;

  Stream<List<LogEntry>> get stream => _controller.stream;
  List<LogEntry> get buffer => List.unmodifiable(_buffer);
  bool get enabled => _enabled;

  Future<void> initialize() async {
    if (_initialized) return;
    _enabled = await FeatureFlags.debugLogsEnabled.isEnabled();
    final storedRaw = await _storage.read(key: _keyBuffer);
    final stored = storedRaw?.split('\n') ?? [];
    _buffer = stored.map((e) {
      final idx = e.indexOf('|');
      if (idx > 0) {
        final ts = DateTime.tryParse(e.substring(0, idx)) ?? DateTime.now();
        final msg = e.substring(idx + 1);
        return LogEntry(ts, msg);
      }
      return LogEntry(DateTime.now(), e);
    }).toList();
    _initialized = true;
    _controller.add(_buffer);
    FlutterBackgroundService().on('log').listen((event) {
      if (event != null && event['message'] != null) {
        dPrint(event['message']);
      }
    });
  }

  Future<void> setEnabled(bool value) async {
    _enabled = value;
    await FeatureFlags.debugLogsEnabled.setFeatureFlag(value);
  }

  Future<void> clear() async {
    _buffer = [];
    _controller.add(_buffer);
    await _storage.delete(key: _keyBuffer);
  }

  Future<void> append(String message) async {
    if (!_enabled) return;
    final entry = LogEntry(DateTime.now(), message);
    _buffer.add(entry);
    // Keep a reasonable cap to avoid unbounded growth
    if (_buffer.length > 2000) {
      _buffer = _buffer.sublist(_buffer.length - 2000);
    }
    _controller.add(_buffer);
    final serialized = _buffer
        .map((e) => "${e.timestamp.toIso8601String()}|${e.message}")
        .join('\n');
    await _storage.write(key: _keyBuffer, value: serialized);
  }
}
