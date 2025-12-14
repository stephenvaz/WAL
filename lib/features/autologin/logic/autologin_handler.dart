import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:wal/core/models/wifi_config.dart';
import 'package:wal/core/models/form_action.dart';
import 'package:wal/core/services/native_bridge.dart';
import 'package:wal/core/utils/debug_utils.dart';

class AutoLoginHandler {
  final NativeBridgeService _nativeBridge = NativeBridgeService();
  HeadlessInAppWebView? _headlessWebView;
  bool _hasInjected = false; // Track if we've already injected

  Future<void> performAutoLogin({
    required WifiConfig config,
    required Function(String status) onStatusUpdate,
  }) async {
    void cleanupWebsiteLogin(InAppWebViewController controller) async {
      printPageContent(controller, "After Injection");
      // clear all caches and cookies to avoid session issues
      await InAppWebViewController.clearAllCache();
      await CookieManager.instance().deleteAllCookies();

      // CLEANUP
      final didUnbind = await _nativeBridge.unbindProcess();
      onStatusUpdate("Login Sequence Complete.\nNetwork Unbound: $didUnbind");
    }

    _hasInjected = false; // Reset for new login attempt
    onStatusUpdate("Binding to WiFi...");
    bool bound = await _nativeBridge.bindProcessToWifi();
    if (!bound) {
      onStatusUpdate("Error: Could not bind to WiFi network.");
      return;
    }

    onStatusUpdate("Loading Portal...");

    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(config.url)),
      initialSettings: InAppWebViewSettings(
        mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
        javaScriptEnabled: true, // Crucial for headless execution
      ),
      onReceivedServerTrustAuthRequest: (controller, challenge) async {
        return ServerTrustAuthResponse(
          action: ServerTrustAuthResponseAction.PROCEED,
        );
      },
      onLoadStop: (controller, url) async {
        // Only inject once on initial page load, not on post-login redirects
        if (_hasInjected) {
          dPrint(
            "[AutoLoginHandler] Subsequent navigation detected, skipping injection: $url",
          );
          cleanupWebsiteLogin(controller);
          return;
        }

        onStatusUpdate("Page Loaded. Attempting Login...");

        printPageContent(controller, "Before Injection");

        // Inject Credentials & Click Login
        await _injectCredentials(controller, config);
        _hasInjected = true; // Mark as injected
        onStatusUpdate("Credentials Injected. Waiting for response...");
        await Future.delayed(const Duration(seconds: 5));
        cleanupWebsiteLogin(controller);
      },
    );

    await _headlessWebView?.run();
  }

  void printPageContent(
    InAppWebViewController controller,
    String identifier,
  ) async {
    var content = await controller.evaluateJavascript(
      source: "document.body.innerText",
    );
    dPrint("[AutoLoginHandler] PAGE CONTENT ($identifier):\n$content");
  }

  Future<void> _injectCredentials(
    InAppWebViewController controller,
    WifiConfig config,
  ) async {
    final jsCode = _generateJsFromActions(config.actions);
    dPrint("[AutoLoginHandler] Executing JS:\n$jsCode");
    await controller.evaluateJavascript(source: jsCode);
  }

  String _generateJsFromActions(List<FormAction> actions) {
    final buffer = StringBuffer();
    buffer.writeln("try {");

    // Sort by order
    final sortedActions = List<FormAction>.from(actions)
      ..sort((a, b) => a.order.compareTo(b.order));

    for (var action in sortedActions) {
      final selector = action.selector.replaceAll("'", "\\'"); // Escape quotes

      switch (action.type) {
        case FormActionType.setValue:
          final value = (action.value ?? '').replaceAll("'", "\\'");

          buffer.writeln("""
            var elem_${action.order} = document.querySelector('$selector');
            if (elem_${action.order}) {
              elem_${action.order}.value = '$value';
              elem_${action.order}.dispatchEvent(new Event('input', { bubbles: true }));
            }
          """);
          break;

        case FormActionType.click:
          buffer.writeln("""
            var btn_${action.order} = document.querySelector('$selector');
            if (btn_${action.order}) {
              btn_${action.order}.disabled = false;
              btn_${action.order}.click();
            }
          """);
          break;
      }
    }

    buffer.writeln("} catch(e) { console.log('AutoLogin Error:', e); }");
    return buffer.toString();
  }

  void dispose() {
    _headlessWebView?.dispose();
  }
}
