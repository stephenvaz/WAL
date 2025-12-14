import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'wal_network_utils_platform_interface.dart';

/// An implementation of [WalNetworkUtilsPlatform] that uses method channels.
class MethodChannelWalNetworkUtils extends WalNetworkUtilsPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('wal_network_utils');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
