import 'package:flutter/foundation.dart';

// Prints only in debug mode
void dPrint(Object? object) {
  if (kDebugMode) {
    print(object);
  }
}