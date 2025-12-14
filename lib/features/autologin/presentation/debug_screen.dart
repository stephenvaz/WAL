import 'package:flutter/material.dart';
import 'package:wal/core/services/storage_service.dart';
import 'package:wal/core/utils/log_service.dart';

class DebugScreen extends StatefulWidget {
  const DebugScreen({super.key});

  @override
  State<DebugScreen> createState() => _DebugScreenState();
}

class _DebugScreenState extends State<DebugScreen> {
  final _logService = LogService();
  final _scrollController = ScrollController();
  bool _initialized = false;
  bool _connectivityCheckEnabled = false;

  @override
  void initState() {
    super.initState();
    _logService.initialize().then((_) async {
      _connectivityCheckEnabled = await FeatureFlags
          .checkConnectivityBeforeLogin
          .isEnabled();
      if (mounted) setState(() => _initialized = true);
    });
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 200),
        curve: Curves.easeOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Debug Logs"),
        actions: [
          if (_logService.enabled)
            IconButton(
              tooltip: 'Clear logs',
              icon: const Icon(Icons.delete_outline),
              onPressed: () async {
                await _logService.clear();
              },
            ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.bug_report, size: 24, color: Colors.orange),
                const SizedBox(width: 8),
                Text("Logs", style: Theme.of(context).textTheme.titleMedium),
                const Spacer(),
                Switch.adaptive(
                  value: _logService.enabled,
                  onChanged: (value) async {
                    await _logService.setEnabled(value);
                    if (mounted) setState(() {});
                  },
                ),
              ],
            ),
            Row(
              children: [
                const Icon(
                  Icons.domain_verification,
                  size: 24,
                  color: Colors.green,
                ),
                const SizedBox(width: 8),
                Text(
                  "Connectivity Verification",
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const Spacer(),
                Switch.adaptive(
                  value: _connectivityCheckEnabled,
                  onChanged: (value) async {
                    await FeatureFlags.checkConnectivityBeforeLogin
                        .setFeatureFlag(value);
                    if (mounted) {
                      setState(() {
                        _connectivityCheckEnabled = value;
                      });
                    }
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (_logService.enabled)
              Expanded(
                child: Padding(
                  padding: EdgeInsets.only(
                    bottom: MediaQuery.of(context).viewInsets.bottom + 32,
                  ),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surface,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: Theme.of(context).dividerColor),
                    ),
                    child: _initialized
                        ? StreamBuilder<List<LogEntry>>(
                            stream: _logService.stream,
                            initialData: _logService.buffer,
                            builder: (context, snapshot) {
                              final logs = snapshot.data ?? const [];
                              // Auto-scroll when new logs arrive
                              WidgetsBinding.instance.addPostFrameCallback(
                                (_) => _scrollToBottom(),
                              );
                              if (logs.isEmpty) {
                                return Center(
                                  child: Text(
                                    "No logs available. \nWaiting for something to happen...",
                                    textAlign: TextAlign.center,
                                    style: Theme.of(context)
                                        .textTheme
                                        .bodyMedium
                                        ?.copyWith(color: Colors.grey),
                                  ),
                                );
                              }
                              return ListView.builder(
                                controller: _scrollController,
                                itemCount: logs.length,
                                itemBuilder: (context, index) {
                                  final entry = logs[index];
                                  return Padding(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 6,
                                    ),
                                    child: Text(
                                      entry.toString(),
                                      style: Theme.of(context)
                                          .textTheme
                                          .bodySmall
                                          ?.copyWith(fontFamily: 'monospace'),
                                    ),
                                  );
                                },
                              );
                            },
                          )
                        : const Center(child: CircularProgressIndicator()),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
