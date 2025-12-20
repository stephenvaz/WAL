import 'package:flutter/material.dart';
import 'package:wal/core/models/form_action.dart';
import 'package:wal/core/models/wifi_config.dart';

class ConfigEditorPage extends StatefulWidget {
  final WifiConfig? existingConfig;
  final Function(WifiConfig) onSave;

  const ConfigEditorPage({
    super.key,
    this.existingConfig,
    required this.onSave,
  });

  @override
  State<ConfigEditorPage> createState() => _ConfigEditorPageState();
}

class _ConfigEditorPageState extends State<ConfigEditorPage> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _ssidController;
  late TextEditingController _urlController;
  late TextEditingController _timeoutController;

  List<FormAction> _actions = [];
  int? _expandedActionIndex;

  @override
  void initState() {
    super.initState();
    final config = widget.existingConfig;
    _ssidController = TextEditingController(text: config?.ssid ?? '');
    _urlController = TextEditingController(text: config?.url ?? '');
    _timeoutController = TextEditingController(
      text: config != null ? config.timeoutInSeconds.toString() : '10.0',
    );
    _actions = List.from(config?.actions ?? []);
  }

  @override
  void dispose() {
    _ssidController.dispose();
    _urlController.dispose();
    super.dispose();
  }

  void _addAction() {
    setState(() {
      _actions.add(
        FormAction(
          type: FormActionType.setValue,
          selector: '',
          order: _actions.length,
        ),
      );
      // Auto-expand the newly added action
      _expandedActionIndex = _actions.length - 1;
    });
  }

  void _removeAction(int index) {
    setState(() {
      _actions.removeAt(index);
      // Reorder
      for (int i = 0; i < _actions.length; i++) {
        _actions[i] = _actions[i].copyWith(order: i);
      }
      _expandedActionIndex = null;
    });
  }

  void _moveAction(int oldIndex, int newIndex) {
    setState(() {
      if (newIndex > oldIndex) newIndex--;
      final action = _actions.removeAt(oldIndex);
      _actions.insert(newIndex, action);
      // Reorder
      for (int i = 0; i < _actions.length; i++) {
        _actions[i] = _actions[i].copyWith(order: i);
      }
    });
  }

  void _saveConfig() {
    if (_formKey.currentState!.validate()) {
      final config = WifiConfig(
        ssid: _ssidController.text.trim(),
        url: _urlController.text.trim(),
        isEnabled: widget.existingConfig?.isEnabled ?? true,
        actions: _actions,
        timeoutInSeconds: _timeoutController.text.isNotEmpty
            ? double.tryParse(_timeoutController.text) ?? 10.0
            : 10.0,
      );
      widget.onSave(config);
      Navigator.pop(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        // Close keyboard when tapping outside
        FocusScope.of(context).unfocus();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            widget.existingConfig != null ? 'Edit Config' : 'New Config',
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.save),
              onPressed: _saveConfig,
              tooltip: 'Save',
            ),
          ],
        ),
        body: Form(
          key: _formKey,
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // Basic Fields
              _buildBasicFields(),

              const SizedBox(height: 24),
              const Divider(),

              // Actions Section
              const SizedBox(height: 16),
              _buildActionsSection(),

              const SizedBox(height: 80),
            ],
          ),
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _addAction,
          icon: const Icon(Icons.add),
          label: const Text('Add Action'),
        ),
      ),
    );
  }

  Widget _buildBasicFields() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Network Settings', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 16),
        TextFormField(
          controller: _ssidController,
          decoration: const InputDecoration(
            labelText: 'WiFi SSID',
            hintText: 'Exact Network Name',
            prefixIcon: Icon(Icons.wifi),
            border: OutlineInputBorder(),
          ),
          validator: (value) =>
              value == null || value.trim().isEmpty ? 'SSID required' : null,
        ),
        const SizedBox(height: 12),
        TextFormField(
          controller: _urlController,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            labelText: 'Portal URL',
            hintText: 'http://192.168.1.1',
            prefixIcon: Icon(Icons.link),
            border: OutlineInputBorder(),
          ),
          validator: (value) {
            if (value == null || value.trim().isEmpty) return 'URL required';
            if (!value.startsWith('http')) {
              return 'Must start with http:// or https://';
            }
            return null;
          },
        ),
        const SizedBox(height: 12),
        TextFormField(
          controller: _timeoutController,
          keyboardType: TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(
            labelText: 'Timeout (seconds)',
            hintText: 'e.g., 10.0',
            prefixIcon: Icon(Icons.timer),
            border: OutlineInputBorder(),
          ),
          validator: (value) {
            if (value == null || value.trim().isEmpty)
              return 'Timeout required';
            final parsed = double.tryParse(value);
            if (parsed == null || parsed <= 0) {
              return 'Enter a valid positive number';
            }
            return null;
          },
        ),
      ],
    );
  }

  Widget _buildActionsSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text('Form Actions', style: Theme.of(context).textTheme.titleLarge),
            const Spacer(),
            Text(
              '${_actions.length} action(s)',
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        const SizedBox(height: 8),
        Text(
          'Actions execute in order. Use CSS selectors or element IDs.',
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 16),

        if (_actions.isEmpty)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Center(
                child: Column(
                  children: [
                    Icon(Icons.touch_app, size: 48, color: Colors.grey[400]),
                    const SizedBox(height: 8),
                    Text(
                      'No actions defined',
                      style: TextStyle(color: Colors.grey[600]),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Tap + to add your first action',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
            ),
          )
        else
          ReorderableListView.builder(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _actions.length,
            onReorder: _moveAction,
            itemBuilder: (context, index) {
              return _buildActionTile(_actions[index], index);
            },
          ),
      ],
    );
  }

  Widget _buildActionTile(FormAction action, int index) {
    return Card(
      key: ValueKey(action.hashCode),
      margin: const EdgeInsets.only(bottom: 12),
      child: ExpansionTile(
        key: ValueKey('expansion_${action.hashCode}'),
        initiallyExpanded: _expandedActionIndex == index,
        onExpansionChanged: (expanded) {
          setState(() {
            _expandedActionIndex = expanded ? index : null;
          });
        },
        leading: CircleAvatar(child: Text('${index + 1}')),
        title: Text(
          action.type == FormActionType.setValue
              ? 'Set Value'
              : 'Click Element',
        ),
        subtitle: Text(
          action.selector.isEmpty ? 'No selector' : action.selector,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        trailing: IconButton(
          icon: const Icon(Icons.delete, color: Colors.red),
          onPressed: () => _removeAction(index),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                DropdownButtonFormField<FormActionType>(
                  initialValue: action.type,
                  decoration: const InputDecoration(
                    labelText: 'Action Type',
                    border: OutlineInputBorder(),
                  ),
                  items: FormActionType.values.map((type) {
                    return DropdownMenuItem(
                      value: type,
                      child: Text(
                        type == FormActionType.setValue
                            ? 'Set Value (Fill Field)'
                            : 'Click (Submit/Button)',
                      ),
                    );
                  }).toList(),
                  onChanged: (newType) {
                    if (newType != null) {
                      setState(() {
                        _actions[index] = action.copyWith(type: newType);
                        // Don't close the expansion tile when type changes
                      });
                    }
                  },
                ),
                const SizedBox(height: 12),
                TextFormField(
                  initialValue: action.selector,
                  decoration: const InputDecoration(
                    labelText: 'Selector',
                    hintText: '#username, input[name="user"]',
                    border: OutlineInputBorder(),
                  ),
                  onChanged: (value) {
                    _actions[index] = action.copyWith(selector: value);
                  },
                ),
                if (action.type == FormActionType.setValue) ...[
                  const SizedBox(height: 12),
                  TextFormField(
                    initialValue: action.value ?? '',
                    decoration: const InputDecoration(
                      labelText: 'Value',
                      hintText: 'Text to enter in the field',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (value) {
                      _actions[index] = action.copyWith(value: value);
                    },
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
