import 'package:flutter_test/flutter_test.dart';
import 'package:wal_network_utils/wal_network_utils.dart';
import 'package:wal_network_utils/wal_network_utils_platform_interface.dart';
import 'package:wal_network_utils/wal_network_utils_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockWalNetworkUtilsPlatform
    with MockPlatformInterfaceMixin
    implements WalNetworkUtilsPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final WalNetworkUtilsPlatform initialPlatform = WalNetworkUtilsPlatform.instance;

  test('$MethodChannelWalNetworkUtils is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelWalNetworkUtils>());
  });

  test('getPlatformVersion', () async {
    WalNetworkUtils walNetworkUtilsPlugin = WalNetworkUtils();
    MockWalNetworkUtilsPlatform fakePlatform = MockWalNetworkUtilsPlatform();
    WalNetworkUtilsPlatform.instance = fakePlatform;

    expect(await walNetworkUtilsPlugin.getPlatformVersion(), '42');
  });
}
