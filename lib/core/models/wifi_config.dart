import 'dart:convert';

class WifiConfig {
  final String ssid;
  final String url;
  final String username;
  final String password;
  final bool isEnabled;

  WifiConfig({
    required this.ssid,
    required this.url,
    required this.username,
    required this.password,
    this.isEnabled = true,
  });

  // Serialization for Storage
  Map<String, dynamic> toMap() {
    return {
      'ssid': ssid,
      'url': url,
      'username': username,
      'password': password,
      'isEnabled': isEnabled,
    };
  }

  factory WifiConfig.fromMap(Map<String, dynamic> map) {
    return WifiConfig(
      ssid: map['ssid'] ?? '',
      url: map['url'] ?? '',
      username: map['username'] ?? '',
      password: map['password'] ?? '',
      isEnabled: map['isEnabled'] ?? true,
    );
  }

  String toJson() => json.encode(toMap());

  factory WifiConfig.fromJson(String source) => 
      WifiConfig.fromMap(json.decode(source));
}