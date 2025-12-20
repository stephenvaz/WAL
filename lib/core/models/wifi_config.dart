import 'dart:convert';
import 'package:wal/core/models/form_action.dart';

class WifiConfig {
  final String ssid;
  final String url;
  final bool isEnabled;
  final List<FormAction> actions;
  final double timeoutInSeconds;

  WifiConfig({
    required this.ssid,
    required this.url,
    this.isEnabled = true,
    List<FormAction>? actions,
    this.timeoutInSeconds = 10.0,
  }) : actions = actions ?? [];

  // Serialization for Storage
  Map<String, dynamic> toMap() {
    return {
      'ssid': ssid,
      'url': url,
      'isEnabled': isEnabled,
      'actions': actions.map((a) => a.toMap()).toList(),
      'timeoutInSeconds': timeoutInSeconds,
    };
  }

  factory WifiConfig.fromMap(Map<String, dynamic> map) {
    List<FormAction> parsedActions =
        (map['actions'] as List<dynamic>?)
            ?.map((a) => FormAction.fromMap(a as Map<String, dynamic>))
            .toList() ??
        [];
    return WifiConfig(
      ssid: map['ssid'] ?? '',
      url: map['url'] ?? '',
      isEnabled: map['isEnabled'] ?? true,
      actions: parsedActions,
      timeoutInSeconds: (map['timeoutInSeconds'] as num?)?.toDouble() ?? 10.0,
    );
  }

  String toJson() => json.encode(toMap());

  factory WifiConfig.fromJson(String source) =>
      WifiConfig.fromMap(json.decode(source));

  WifiConfig copyWith({
    String? ssid,
    String? url,
    bool? isEnabled,
    List<FormAction>? actions,
    double? timeoutInSeconds,
  }) {
    return WifiConfig(
      ssid: ssid ?? this.ssid,
      url: url ?? this.url,
      isEnabled: isEnabled ?? this.isEnabled,
      actions: actions ?? this.actions,
      timeoutInSeconds: timeoutInSeconds ?? this.timeoutInSeconds,
    );
  }
}
