import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:location/location.dart' as loc;
import 'package:wal/core/models/wifi_config.dart';
import 'package:wal/core/services/storage_service.dart';
import 'package:wal/features/autologin/presentation/config_sheet.dart';
import 'package:wal/features/autologin/presentation/debug_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

// Add WidgetsBindingObserver to detect when user returns from Settings
class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  final StorageService _storage = StorageService();
  List<WifiConfig> _configs = [];
  bool _isLoading = true;

  // Permission States
  bool _isLocationGranted = true;
  bool _isNotificationGranted = true;
  bool _isLocationServiceEnabled = true;
  bool _isSystemAlertWindowGranted = true;

  // Add this to your State class
  Timer? _statusPoller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    // Ask for everything ONCE when app launches
    _checkPermissions(requestDialog: true);

    _loadConfigs();

    // Poll every 3 seconds, but ONLY CHECK (don't pop up dialogs)
    _statusPoller = Timer.periodic(const Duration(seconds: 3), (timer) {
      _checkPermissions(requestDialog: false); // Silent check
    });
  }

  @override
  void dispose() {
    _statusPoller?.cancel(); // Don't forget to cancel!
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Silent check is usually enough here
      _checkPermissions(requestDialog: false); 
    }
  }

  /// Checks permissions and updates Service Notification text
  Future<void> _checkPermissions({bool requestDialog = false}) async {
    // 1. Check/Request Runtime Permissions (App <-> OS)
    // We only request these if explicitly asked (requestDialog = true)
    PermissionStatus locStatus;
    PermissionStatus notifStatus;

    if (requestDialog) {
      locStatus = await Permission.location.request();
      notifStatus = await Permission.notification.request();
    } else {
      // Just check status without asking
      locStatus = await Permission.location.status;
      notifStatus = await Permission.notification.status;
    }

    // 2. Check/Request Location Service (GPS Chip)
    loc.Location location = loc.Location();
    bool serviceEnabled = await location.serviceEnabled();

    if (!serviceEnabled && requestDialog) {
      // Only show the pop-up if requestDialog is TRUE
      serviceEnabled = await location.requestService();
    }

    // 3. NEW: Check System Alert Window (Overlay)
    // This permission handles its own "Request" behavior (opens settings page directly)
    var overlayStatus = await Permission.systemAlertWindow.status;
    if (overlayStatus.isDenied && requestDialog) {
       await Permission.systemAlertWindow.request();
       // Re-check after returning from settings
       overlayStatus = await Permission.systemAlertWindow.status;
    }

    // 4. Update State
    if (mounted) {
      setState(() {
        _isLocationGranted = locStatus.isGranted;
        _isNotificationGranted = notifStatus.isGranted;
        _isLocationServiceEnabled = serviceEnabled;
        _isSystemAlertWindowGranted = overlayStatus.isGranted;
      });
    }

    // 4. Update Background Service Notification
    final service = FlutterBackgroundService();

    if (!_isLocationGranted) {
      service.invoke('update_notification', {
        'title': 'Action Required',
        'content': 'Permission denied. Cannot scan WiFi.',
      });
    } else if (!_isLocationServiceEnabled) {
      service.invoke('update_notification', {
        'title': 'AutoLogin Paused',
        'content': 'Location (GPS) disabled. Cannot scan WiFi.',
      });
    } else {
      service.invoke('update_notification', {
        'title': 'AutoWiFi Active',
        'content': 'Monitoring network...',
      });
    }
  }

  // ... (Keep _loadConfigs, _saveConfig, _deleteConfig, _toggleEnable as they were)
  Future<void> _loadConfigs() async {
    setState(() => _isLoading = true);
    final data = await _storage.getAllConfigs();
    setState(() {
      _configs = data;
      _isLoading = false;
    });
  }

  Future<void> _saveConfig(WifiConfig config) async {
    await _storage.saveConfig(config);
    _loadConfigs();
  }

  Future<void> _deleteConfig(String ssid) async {
    await _storage.deleteConfig(ssid);
    _loadConfigs();
  }

  Future<void> _toggleEnable(WifiConfig config) async {
    final updated = WifiConfig(
      ssid: config.ssid,
      url: config.url,
      username: config.username,
      password: config.password,
      isEnabled: !config.isEnabled,
    );
    await _saveConfig(updated);
  }

  void _showConfigSheet({WifiConfig? config}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) =>
          ConfigSheet(existingConfig: config, onSave: _saveConfig),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("WAL - WiFi Auto Login"),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: "Add Config",
            onPressed: () => _showConfigSheet(),
          ),
          IconButton(
            icon: const Icon(Icons.bug_report),
            tooltip: "Debug Logs",
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const DebugScreen()),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // THE WARNING BANNER
          _buildPermissionWarning(),

          // THE LIST
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _configs.isEmpty
                ? _buildEmptyState()
                : ListView.builder(
                    itemCount: _configs.length,
                    itemBuilder: (context, index) {
                      return _buildConfigTile(_configs[index]);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionWarning() {
    // Hide if all 4 checks pass
    if (_isLocationGranted && 
        _isNotificationGranted && 
        _isLocationServiceEnabled && 
        _isSystemAlertWindowGranted) {
      return const SizedBox.shrink();
    }

    String errorText = "";
    IconData icon = Icons.warning;
    VoidCallback? onTapAction;

    // Prioritize warnings
    if (!_isNotificationGranted) {
      errorText = "Notifications disabled. Service status hidden.";
      icon = Icons.notifications_off;
      onTapAction = openAppSettings;
    } else if (!_isLocationGranted) {
      errorText = "Location denied. Cannot detect WiFi.";
      icon = Icons.location_off;
      onTapAction = openAppSettings;
    } else if (!_isLocationServiceEnabled) {
      errorText = "Location (GPS) is OFF. Tap to enable.";
      icon = Icons.location_disabled;
      onTapAction = () async {
        await _checkPermissions(requestDialog: true);
      };
    } else if (!_isSystemAlertWindowGranted) {
      // NEW WARNING
      errorText = "Need 'Display over Apps' to auto-launch.";
      icon = Icons.layers_clear;
      onTapAction = () async {
         await Permission.systemAlertWindow.request();
      };
    }

    return Material(
      color: Theme.of(context).colorScheme.errorContainer,
      child: InkWell(
        onTap: onTapAction,
        child: Padding(
          padding: const EdgeInsets.all(12.0),
          child: Row(
            children: [
              Icon(icon, color: Theme.of(context).colorScheme.onErrorContainer),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  errorText,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onErrorContainer,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              Icon(Icons.arrow_forward_ios, 
                size: 16, 
                color: Theme.of(context).colorScheme.onErrorContainer
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ... (Keep _buildConfigTile and _buildEmptyState exactly as they were)
  Widget _buildConfigTile(WifiConfig config) {
    return Dismissible(
      key: Key(config.ssid),
      background: Container(
        color: Colors.green,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: Icon(
          config.isEnabled ? Icons.unpublished : Icons.check_circle,
          color: Colors.white,
        ),
      ),
      secondaryBackground: Container(
        color: Colors.red,
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: const Icon(Icons.delete, color: Colors.white),
      ),
      confirmDismiss: (direction) async {
        if (direction == DismissDirection.startToEnd) {
          await _toggleEnable(config);
          return false;
        } else {
          return await showDialog(
            context: context,
            builder: (ctx) => AlertDialog(
              title: const Text("Delete Config?"),
              content: Text("Remove settings for ${config.ssid}?"),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(false),
                  child: const Text("Cancel"),
                ),
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(true),
                  child: const Text("Delete"),
                ),
              ],
            ),
          );
        }
      },
      onDismissed: (direction) {
        if (direction == DismissDirection.endToStart) {
          _deleteConfig(config.ssid);
        }
      },
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: config.isEnabled
              ? Theme.of(context).colorScheme.primaryContainer
              : Colors.grey[300],
          child: Icon(
            Icons.wifi,
            color: config.isEnabled
                ? Theme.of(context).colorScheme.primary
                : Colors.grey,
          ),
        ),
        title: Text(
          config.ssid,
          style: TextStyle(
            decoration: config.isEnabled ? null : TextDecoration.lineThrough,
            color: config.isEnabled ? null : Colors.grey,
          ),
        ),
        subtitle: Text(config.url),
        trailing: PopupMenuButton(
          onSelected: (value) {
            if (value == 'edit') _showConfigSheet(config: config);
            if (value == 'toggle') _toggleEnable(config);
            if (value == 'delete') _deleteConfig(config.ssid);
          },
          itemBuilder: (context) => [
            const PopupMenuItem(
              value: 'edit',
              child: Row(
                children: [Icon(Icons.edit), SizedBox(width: 8), Text("Edit")],
              ),
            ),
            PopupMenuItem(
              value: 'toggle',
              child: Row(
                children: [
                  Icon(config.isEnabled ? Icons.unpublished : Icons.check),
                  const SizedBox(width: 8),
                  Text(config.isEnabled ? "Disable" : "Enable"),
                ],
              ),
            ),
            const PopupMenuItem(
              value: 'delete',
              child: Row(
                children: [
                  Icon(Icons.delete),
                  SizedBox(width: 8),
                  Text("Delete"),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.wifi_off, size: 80, color: Colors.grey[400]),
          const SizedBox(height: 20),
          Text(
            "No Configs Found",
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(color: Colors.grey),
          ),
          const SizedBox(height: 10),
          const Text("Tap + to add a new auto-login"),
        ],
      ),
    );
  }
}
