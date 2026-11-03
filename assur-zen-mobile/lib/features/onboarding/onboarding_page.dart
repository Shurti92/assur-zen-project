import 'package:flutter/material.dart';

import '../../app/app_scope.dart';
import '../../app/routes.dart';
import '../../core/ui.dart';

/// Trois écrans de présentation, avec « Passer ».
class OnboardingPage extends StatefulWidget {
  const OnboardingPage({super.key});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage> {
  final _controller = PageController();
  int _index = 0;

  static const _slides = [
    (Icons.verified_user_outlined, 'Votre assurance, dans votre poche',
        'Devis, souscription et attestations depuis votre téléphone, en quelques minutes.'),
    (Icons.account_balance_wallet_outlined, 'Payez simplement, en FCFA',
        'Orange Money, MTN MoMo, Moov Money ou Wave. Vos reçus sont toujours disponibles.'),
    // Aucune promesse de disponibilité écrite en dur : les horaires viennent du serveur.
    (Icons.support_agent_outlined, 'Un conseiller à votre écoute',
        'Sinistre, question ou renouvellement : contactez-nous depuis l’application.'),
  ];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _finish() async {
    await context.store.setOnboardingDone();
    if (mounted) context.resetTo(AppRoutes.welcome);
  }

  void _next() {
    if (_index < _slides.length - 1) {
      _controller.nextPage(duration: const Duration(milliseconds: 280), curve: Curves.easeOut);
    } else {
      _finish();
    }
  }

  @override
  Widget build(BuildContext context) {
    final last = _index == _slides.length - 1;
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
          child: Column(
            children: [
              Align(alignment: Alignment.centerRight, child: AzTextLink('Passer', onPressed: _finish, fontSize: 15)),
              Expanded(
                child: PageView.builder(
                  controller: _controller,
                  itemCount: _slides.length,
                  onPageChanged: (i) => setState(() => _index = i),
                  itemBuilder: (context, i) {
                    final (icon, title, text) = _slides[i];
                    return _Slide(icon: icon, title: title, text: text);
                  },
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Semantics(
                    label: 'Page ${_index + 1} sur ${_slides.length}',
                    child: Row(
                      children: [
                        for (var i = 0; i < _slides.length; i++)
                          AnimatedContainer(
                            duration: const Duration(milliseconds: 220),
                            margin: const EdgeInsets.only(right: 6),
                            width: i == _index ? 24 : 8,
                            height: 8,
                            decoration: BoxDecoration(
                              color: i == _index ? AzColors.blue700 : AzColors.blue100,
                              borderRadius: BorderRadius.circular(4),
                            ),
                          ),
                      ],
                    ),
                  ),
                  const Spacer(),
                  SizedBox(width: 168, child: AzButton(last ? 'Commencer' : 'Suivant', onPressed: _next)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Slide extends StatelessWidget {
  const _Slide({required this.icon, required this.title, required this.text});

  final IconData icon;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(builder: (context, c) {
      final art = (c.maxHeight * 0.45).clamp(140.0, 300.0);
      // Défilement possible sur les petits écrans ou avec un texte agrandi.
      return SingleChildScrollView(
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: c.maxHeight),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _Art(icon: icon, height: art),
              const SizedBox(height: 28),
              Text(title, style: AzText.headline.copyWith(fontSize: 28, height: 34 / 28)),
              const SizedBox(height: 10),
              Text(text, style: AzText.body.copyWith(color: AzColors.muted)),
            ],
          ),
        ),
      );
    });
  }
}

class _Art extends StatelessWidget {
  const _Art({required this.icon, required this.height});

  final IconData icon;
  final double height;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      width: double.infinity,
      decoration: BoxDecoration(color: AzColors.blue50, borderRadius: BorderRadius.circular(28)),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Positioned(
            left: -60,
            top: -80,
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: AzColors.blue100, width: 1.5)),
            ),
          ),
          Positioned(
            right: -50,
            bottom: -60,
            child: Container(
              width: 180,
              height: 180,
              decoration: const BoxDecoration(shape: BoxShape.circle, color: AzColors.orange50),
            ),
          ),
          Container(
            width: 112,
            height: 112,
            decoration: BoxDecoration(
              color: AzColors.blue700,
              borderRadius: BorderRadius.circular(34),
              boxShadow: const [BoxShadow(color: Color(0x4D033892), blurRadius: 40, offset: Offset(0, 16))],
            ),
            child: Icon(icon, size: 52, color: Colors.white),
          ),
        ],
      ),
    );
  }
}
