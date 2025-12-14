import 'dart:convert';
import 'package:wal/core/models/form_action.dart';

class WifiConfig {
  final String ssid;
  final String url;
  final bool isEnabled;
  final List<FormAction> actions; // Custom form actions

  WifiConfig({
    required this.ssid,
    required this.url,
    this.isEnabled = true,
    List<FormAction>? actions,
  }) : actions = actions ?? [];

  // Serialization for Storage
  Map<String, dynamic> toMap() {
    return {
      'ssid': ssid,
      'url': url,
      'isEnabled': isEnabled,
      'actions': actions.map((a) => a.toMap()).toList(),
    };
  }

  factory WifiConfig.fromMap(Map<String, dynamic> map) {
    // For backward compatibility: if old config has username/password but no actions,
    // create default setValue actions
    List<FormAction> parsedActions =
        (map['actions'] as List<dynamic>?)
            ?.map((a) => FormAction.fromMap(a as Map<String, dynamic>))
            .toList() ??
        [];

    // Migration: convert old username/password configs to actions
    if (parsedActions.isEmpty &&
        map.containsKey('username') &&
        map.containsKey('password')) {
      parsedActions = [
        FormAction(
          type: FormActionType.setValue,
          selector: "#username, input[name='username']",
          value: map['username'] ?? '',
          order: 0,
        ),
        FormAction(
          type: FormActionType.setValue,
          selector: "#password, input[name='password']",
          value: map['password'] ?? '',
          order: 1,
        ),
        FormAction(
          type: FormActionType.click,
          selector: "button[type='submit'], input[type='submit']",
          order: 2,
        ),
      ];
    }

    return WifiConfig(
      ssid: map['ssid'] ?? '',
      url: map['url'] ?? '',
      isEnabled: map['isEnabled'] ?? true,
      actions: parsedActions,
    );
  }

  String toJson() => json.encode(toMap());

  factory WifiConfig.fromJson(String source) =>
      WifiConfig.fromMap(json.decode(source));
}
