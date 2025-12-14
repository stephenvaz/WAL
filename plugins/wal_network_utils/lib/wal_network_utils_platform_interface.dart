import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'wal_network_utils_method_channel.dart';

abstract class WalNetworkUtilsPlatform extends PlatformInterface {
  /// Constructs a WalNetworkUtilsPlatform.
  WalNetworkUtilsPlatform() : super(token: _token);

  static final Object _token = Object();

  static WalNetworkUtilsPlatform _instance = MethodChannelWalNetworkUtils();

  /// The default instance of [WalNetworkUtilsPlatform] to use.
  ///
  /// Defaults to [MethodChannelWalNetworkUtils].
  static WalNetworkUtilsPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [WalNetworkUtilsPlatform] when
  /// they register themselves.
  static set instance(WalNetworkUtilsPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
