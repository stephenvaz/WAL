import 'package:flutter/foundation.dart';
import 'package:wal/core/utils/log_service.dart';

// Prints only in debug mode
void dPrint(Object? object) {
  if (kDebugMode) {
    print(object);
  }
  // Also log to LogService
  LogService().append(object?.toString() ?? 'null');
}