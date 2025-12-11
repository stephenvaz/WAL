import 'package:flutter/material.dart';

class LoginProcessingScreen extends StatelessWidget {
  final String statusMessage;

  const LoginProcessingScreen({
    super.key, 
    this.statusMessage = "Logging in..."
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // Immersive dark background to reduce glare if waking up at night 
      backgroundColor: Colors.black.withValues(alpha: 0.9), 
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const SizedBox(
              width: 60,
              height: 60,
              child: CircularProgressIndicator(
                strokeWidth: 4,
                valueColor: AlwaysStoppedAnimation<Color>(Colors.blueAccent),
              ),
            ),
            const SizedBox(height: 30),
            Text(
              "Auto WiFi Login",
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                color: Colors.white,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 10),
            Text(
              statusMessage,
              style: TextStyle(color: Colors.white.withValues(alpha: 0.7)),
            ),
          ],
        ),
      ),
    );
  }
}