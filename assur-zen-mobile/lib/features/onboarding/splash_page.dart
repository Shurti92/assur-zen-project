import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';

/// Écran de lancement : logo, slogan, puis redirection.
class SplashPage extends StatefulWidget {
  const SplashPage({super.key});

  @override
  State<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends State<SplashPage> {
  @override
  void initState() {
    super.initState();
    Future.delayed(const Duration(milliseconds: 1500), _next);
  }

  void _next() {
    if (!mounted) return;
    final store = context.store;
    if (!store.onboardingDone) {
      context.resetTo(AppRoutes.onboarding);
    } else if (store.knownPhone != null) {
      context.resetTo(AppRoutes.login);
    } else {
      context.resetTo(AppRoutes.welcome);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: Stack(
        children: [
          Positioned(
            left: -160,
            bottom: -200,
            child: Container(
              width: 420,
              height: 420,
              decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: AzColors.orange50, width: 2)),
            ),
          ),
          Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const AzLogo(height: 80),
                const SizedBox(height: 20),
                Text('Votre sérénité, notre priorité', style: AzText.body.copyWith(color: AzColors.blue700, fontWeight: FontWeight.w700)),
                const SizedBox(height: 36),
                const SizedBox(
                  width: 28,
                  height: 28,
                  child: CircularProgressIndicator(strokeWidth: 3, color: AzColors.orange500, backgroundColor: AzColors.orange50),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
