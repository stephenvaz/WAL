import 'package:flutter/material.dart';
import 'package:wal/core/models/wifi_config.dart';

class ConfigSheet extends StatefulWidget {
  final WifiConfig? existingConfig; // Null if adding new
  final Function(WifiConfig) onSave;

  const ConfigSheet({super.key, this.existingConfig, required this.onSave});

  @override
  State<ConfigSheet> createState() => _ConfigSheetState();
}

class _ConfigSheetState extends State<ConfigSheet> {
  final _formKey = GlobalKey<FormState>();
  late TextEditingController _ssidController;
  late TextEditingController _urlController;
  late TextEditingController _userController;
  late TextEditingController _passController;

  @override
  void initState() {
    super.initState();
    final config = widget.existingConfig;
    _ssidController = TextEditingController(text: config?.ssid ?? '');
    _urlController = TextEditingController(text: config?.url ?? '');
    _userController = TextEditingController(text: config?.username ?? '');
    _passController = TextEditingController(text: config?.password ?? '');
  }

  @override
  Widget build(BuildContext context) {
    final isEditing = widget.existingConfig != null;
    
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
        left: 20,
        right: 20,
        top: 20,
      ),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                isEditing ? "Edit Configuration" : "New Auto-Login",
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 20),
              
              // SSID Input
              TextFormField(
                controller: _ssidController,
                readOnly: isEditing, // Cannot change SSID key while editing
                decoration: const InputDecoration(
                  labelText: "WiFi SSID",
                  hintText: "Exact Network Name",
                  prefixIcon: Icon(Icons.wifi),
                  border: OutlineInputBorder(),
                ),
                validator: (value) => 
                    value == null || value.isEmpty ? 'SSID is required' : null,
              ),
              const SizedBox(height: 10),

              // URL Input
              TextFormField(
                controller: _urlController,
                keyboardType: TextInputType.url,
                decoration: const InputDecoration(
                  labelText: "Portal URL",
                  hintText: "http://192.168.1.1",
                  prefixIcon: Icon(Icons.link),
                  border: OutlineInputBorder(),
                ),
                validator: (value) {
                  if (value == null || value.isEmpty) return 'URL is required';
                  if (!value.startsWith('http')) return 'Must start with http:// or https://';
                  return null;
                },
              ),
              const SizedBox(height: 10),

              // Username
              TextFormField(
                controller: _userController,
                decoration: const InputDecoration(
                  labelText: "Username",
                  prefixIcon: Icon(Icons.person),
                  border: OutlineInputBorder(),
                ),
                validator: (value) => value!.isEmpty ? 'Username is required' : null,
              ),
              const SizedBox(height: 10),

              // Password
              TextFormField(
                controller: _passController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: "Password",
                  prefixIcon: Icon(Icons.key),
                  border: OutlineInputBorder(),
                ),
                validator: (value) => value!.isEmpty ? 'Password is required' : null,
              ),
              const SizedBox(height: 20),

              // Buttons
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text("Cancel"),
                  ),
                  const SizedBox(width: 10),
                  FilledButton(
                    onPressed: () {
                      if (_formKey.currentState!.validate()) {
                        final newConfig = WifiConfig(
                          ssid: _ssidController.text,
                          url: _urlController.text,
                          username: _userController.text,
                          password: _passController.text,
                          isEnabled: widget.existingConfig?.isEnabled ?? true,
                        );
                        widget.onSave(newConfig);
                        Navigator.pop(context);
                      }
                    },
                    child: const Text("Save"),
                  ),
                ],
              ),
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }
}