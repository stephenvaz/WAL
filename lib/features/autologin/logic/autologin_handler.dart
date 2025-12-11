import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:wal/core/services/native_bridge.dart';
import 'package:wal/core/utils/debug_utils.dart';

class AutoLoginHandler {
  final NativeBridgeService _nativeBridge = NativeBridgeService();
  HeadlessInAppWebView? _headlessWebView;

  Future<void> performAutoLogin({
    required String url,
    required String username,
    required String password,
    required Function(String status) onStatusUpdate,
  }) async {
    
    onStatusUpdate("Binding to WiFi...");
    bool bound = await _nativeBridge.bindProcessToWifi();
    if (!bound) {
      onStatusUpdate("Error: Could not bind to WiFi network.");
      return;
    }

    onStatusUpdate("Loading Portal...");
    
    _headlessWebView = HeadlessInAppWebView(
      initialUrlRequest: URLRequest(url: WebUri(url)),
      initialSettings: InAppWebViewSettings(
        mixedContentMode: MixedContentMode.MIXED_CONTENT_ALWAYS_ALLOW,
        // Crucial for headless execution
        javaScriptEnabled: true,
      ),
      onReceivedServerTrustAuthRequest: (controller, challenge) async {
        return ServerTrustAuthResponse(action: ServerTrustAuthResponseAction.PROCEED);
      },
      onLoadStop: (controller, url) async {
        onStatusUpdate("Page Loaded. Attempting Login...");
        
        // 1. Inject Credentials & Click Login
        await _injectCredentials(controller, username, password);
        
        // 2. Wait for the page to process the login
        // (Wait longer if the network is slow)
        onStatusUpdate("Waiting for response...");
        await Future.delayed(const Duration(seconds: 4));
        
        // 3. SCRAPE PAGE CONTENT
        // We get the visible text of the body to verify success
        var pageContent = await controller.evaluateJavascript(
          source: "document.body.innerText"
        );

        dPrint("------------------------------------------------");
        dPrint("PAGE CONTENT AFTER LOGIN ATTEMPT:");
        dPrint(pageContent);
        dPrint("------------------------------------------------");

        // 4. CLEANUP
        await _nativeBridge.unbindProcess();
        onStatusUpdate("Login Sequence Complete. Check Logs.");
      },
    );

    await _headlessWebView?.run();
  }

  Future<void> _injectCredentials(InAppWebViewController controller, String user, String pass) async {
    const String jsCode = """
      try {
        // 1. Fill User
        var userField = document.getElementById('username') || document.querySelector('input[name="username"]');
        if(userField) {
           userField.value = '%USER%';
           // Trigger input events in case the site uses React/Angular validation
           userField.dispatchEvent(new Event('input', { bubbles: true }));
        }

        // 2. Fill Pass
        var passField = document.getElementById('password') || document.querySelector('input[name="password"]');
        if(passField) {
           passField.value = '%PASS%';
           passField.dispatchEvent(new Event('input', { bubbles: true }));
        }

        // 3. Click Submit
        var btn = document.querySelector('button[type="submit"]') || document.querySelector('input[type="submit"]');
        if(btn) {
           btn.click();
        } else {
           // Fallback: Try submitting the form directly if button is missing
           var form = document.getElementById('loginForm') || document.querySelector('form');
           if(form) form.submit();
        }
      } catch(e) { console.log(e); }
    """;

    String formattedJs = jsCode
        .replaceAll('%USER%', user)
        .replaceAll('%PASS%', pass);

    await controller.evaluateJavascript(source: formattedJs);
  }

  void dispose() {
    _headlessWebView?.dispose();
  }
}