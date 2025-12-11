// import 'package:flutter/material.dart';
// import 'package:flutter_background_service/flutter_background_service.dart';
// import 'package:permission_handler/permission_handler.dart';
// import '../../../core/services/storage_service.dart';
// import '../logic/autologin_handler.dart';

// class WifiSetupScreen extends StatefulWidget {
//   const WifiSetupScreen({super.key});

//   @override
//   State<WifiSetupScreen> createState() => _WifiSetupScreenState();
// }

// class _WifiSetupScreenState extends State<WifiSetupScreen> {
//   // Controllers
//   final _ssidController = TextEditingController();
//   final _urlController = TextEditingController();
//   final _userController = TextEditingController();
//   final _passController = TextEditingController();

//   // Logic Handlers
//   final StorageService _storageService = StorageService();
//   final AutoLoginHandler _loginHandler = AutoLoginHandler();

//   String _statusMessage = "Ready";
//   bool _isProcessing = false;

//   @override
//   void initState() {
//     super.initState();
//     _requestPermissions();
//     _listenToBackgroundService();
//   }

//   @override
//   void dispose() {
//     _loginHandler.dispose();
//     super.dispose();
//   }

//   Future<void> _requestPermissions() async {
//     await [
//       Permission.location, // Required for SSID
//       Permission.notification
//     ].request();
//   }

//   void _listenToBackgroundService() {
//     FlutterBackgroundService().on('trigger_login').listen((event) {
//       if (event != null && event['data'] != null) {
//         String data = event['data'];
//         List<String> parts = data.split('|');
//         if (parts.length >= 3) {
//           _runLoginSequence(parts[0], parts[1], parts[2]);
//         }
//       }
//     });
//   }

//   Future<void> _saveConfig() async {
//     if (_ssidController.text.isEmpty) return;
    
//     await _storageService.saveCredentials(
//       _ssidController.text,
//       _urlController.text,
//       _userController.text,
//       _passController.text,
//     );

//     if (mounted) {
//       ScaffoldMessenger.of(context).showSnackBar(
//         SnackBar(content: Text('Saved Auto-Login for ${_ssidController.text}')),
//       );
//     }
//   }

//   Future<void> _runLoginSequence(String url, String user, String pass) async {
//     setState(() => _isProcessing = true);
    
//     await _loginHandler.performAutoLogin(
//       url: url,
//       username: user,
//       password: pass,
//       onStatusUpdate: (status) {
//         setState(() => _statusMessage = status);
//       },
//     );

//     setState(() => _isProcessing = false);
//   }

//   @override
//   Widget build(BuildContext context) {
//     final colorScheme = Theme.of(context).colorScheme;

//     return Scaffold(
//       appBar: AppBar(title: const Text("Auto WiFi Login")),
//       body: Padding(
//         padding: const EdgeInsets.all(20.0),
//         child: SingleChildScrollView(
//           child: Column(
//             crossAxisAlignment: CrossAxisAlignment.stretch,
//             children: [
//               if (_isProcessing) ...[
//                 LinearProgressIndicator(color: colorScheme.primary),
//                 const SizedBox(height: 10),
//                 Text(
//                   _statusMessage, 
//                   textAlign: TextAlign.center,
//                   style: TextStyle(color: colorScheme.secondary),
//                 ),
//                 const SizedBox(height: 20),
//               ],
              
//               _buildConfigCard(context),
//             ],
//           ),
//         ),
//       ),
//     );
//   }

//   Widget _buildConfigCard(BuildContext context) {
//     return Card(
//       elevation: 0,
//       color: Theme.of(context).colorScheme.surfaceContainerHighest,
//       child: Padding(
//         padding: const EdgeInsets.all(20),
//         child: Column(
//           children: [
//             Text(
//               "Configure Network",
//               style: Theme.of(context).textTheme.titleLarge,
//             ),
//             const SizedBox(height: 15),
//             _buildTextField(_ssidController, "WiFi SSID", Icons.wifi),
//             const SizedBox(height: 10),
//             _buildTextField(_urlController, "Portal URL", Icons.link, hint: "http://..."),
//             const SizedBox(height: 10),
//             _buildTextField(_userController, "Username", Icons.person),
//             const SizedBox(height: 10),
//             _buildTextField(_passController, "Password", Icons.key, isObscure: true),
//             const SizedBox(height: 20),
//             FilledButton.icon(
//               onPressed: _saveConfig,
//               icon: const Icon(Icons.save),
//               label: const Text("Save Configuration"),
//             ),
//           ],
//         ),
//       ),
//     );
//   }

//   Widget _buildTextField(
//     TextEditingController controller, 
//     String label, 
//     IconData icon, 
//     {bool isObscure = false, String? hint}
//   ) {
//     return TextField(
//       controller: controller,
//       obscureText: isObscure,
//       decoration: InputDecoration(
//         labelText: label,
//         hintText: hint,
//         border: const OutlineInputBorder(),
//         prefixIcon: Icon(icon),
//         filled: true,
//         fillColor: Theme.of(context).colorScheme.surface,
//       ),
//     );
//   }
// }