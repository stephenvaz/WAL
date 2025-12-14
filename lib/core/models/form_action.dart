import 'dart:convert';

enum FormActionType {
  setValue,
  click;

  @override
  String toString() => name;

  static FormActionType fromString(String value) {
    return FormActionType.values.firstWhere(
      (e) => e.name == value,
      orElse: () => FormActionType.setValue,
    );
  }
}

class FormAction {
  final FormActionType type;
  final String selector; // CSS selector or element ID
  final String? value; // Value to set (for setValue type)
  final int order; // Execution order

  FormAction({
    required this.type,
    required this.selector,
    this.value,
    required this.order,
  });

  Map<String, dynamic> toMap() {
    return {
      'type': type.toString(),
      'selector': selector,
      'value': value,
      'order': order,
    };
  }

  factory FormAction.fromMap(Map<String, dynamic> map) {
    return FormAction(
      type: FormActionType.fromString(map['type'] ?? 'setValue'),
      selector: map['selector'] ?? '',
      value: map['value'],
      order: map['order'] ?? 0,
    );
  }

  String toJson() => json.encode(toMap());

  factory FormAction.fromJson(String source) =>
      FormAction.fromMap(json.decode(source));

  FormAction copyWith({
    FormActionType? type,
    String? selector,
    String? value,
    int? order,
  }) {
    return FormAction(
      type: type ?? this.type,
      selector: selector ?? this.selector,
      value: value ?? this.value,
      order: order ?? this.order,
    );
  }
}
