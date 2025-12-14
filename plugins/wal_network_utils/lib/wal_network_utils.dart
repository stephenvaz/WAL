
import 'wal_network_utils_platform_interface.dart';

class WalNetworkUtils {
  Future<String?> getPlatformVersion() {
    return WalNetworkUtilsPlatform.instance.getPlatformVersion();
  }
}
