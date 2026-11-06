import 'dart:async';

import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../../app/app_scope.dart';
import '../../core/ui.dart';
import '../../data/models/promo_popup.dart';
import '../catalog/catalog_page.dart' show openProduct;

/// Pop-up publicitaire de l'accueil, paramétré au back-office.
///
/// Une seule vérification par lancement de l'application. Sans campagne, sans réseau ou si la fréquence
/// l'interdit, rien ne s'affiche. Le client peut toujours fermer (croix, toucher à côté, retour Android).
abstract final class PromoPopups {
  static bool _checked = false;

  /// Tests seulement : remplace le chargement réseau des images.
  @visibleForTesting
  static ImageProvider Function(String url)? debugImageFactory;

  @visibleForTesting
  static void resetForTest() {
    _checked = false;
    debugImageFactory = null;
  }

  static Future<void> maybeShow(BuildContext context, {DateTime? now}) async {
    if (_checked) return;
    _checked = true;
    final repos = context.repos;
    final store = context.store;
    final list = await repos.promotions.getPopups();
    if (list.isEmpty || !context.mounted) return;
    final popup = pickPopup(list, store.readPopupsSeen(), now ?? DateTime.now());
    if (popup == null) return;

    await Future<void>.delayed(Duration(seconds: popup.displayAfterSeconds));
    // Seulement si l'accueil est toujours l'écran affiché (pas pendant un devis, un paiement, une connexion).
    if (!context.mounted || ModalRoute.of(context)?.isCurrent != true) return;

    var counted = false;
    void onReady() {
      if (counted) return;
      counted = true;
      store.markPopupSeen(popup.id, popup.version ?? '', dayKey(now ?? DateTime.now()));
      repos.promotions.sendPopupEvent(popup.id, 'VIEW');
    }

    final action = await showGeneralDialog<bool>(
      context: context,
      barrierDismissible: true,
      barrierLabel: 'Fermer',
      barrierColor: AzColors.scrim,
      transitionDuration: const Duration(milliseconds: 200),
      pageBuilder: (dialogContext, _, __) => PromoPopupView(popup: popup, onReady: onReady),
      transitionBuilder: (_, animation, __, child) => FadeTransition(opacity: animation, child: child),
    );
    if (!counted) return; // le média n'a pas pu s'afficher : rien n'a été vu
    if (action == true) {
      repos.promotions.sendPopupEvent(popup.id, 'CLICK');
      if (!context.mounted) return;
      if (popup.productId != null) {
        openProduct(context, popup.productId!);
      } else if (popup.linkUrl != null) {
        Launch.url(context, popup.linkUrl!);
      }
    } else {
      repos.promotions.sendPopupEvent(popup.id, 'CLOSE');
    }
  }
}

/// Contenu du pop-up : croix de fermeture, média (image ou vidéo), bouton facultatif.
class PromoPopupView extends StatefulWidget {
  const PromoPopupView({super.key, required this.popup, this.onReady, this.videoFactory});

  final PromoPopup popup;

  /// Appelé quand le média est visible.
  final VoidCallback? onReady;

  /// Pour les tests : remplace la lecture vidéo réseau.
  final VideoPlayerController Function(Uri uri)? videoFactory;

  @override
  State<PromoPopupView> createState() => _PromoPopupViewState();
}

class _PromoPopupViewState extends State<PromoPopupView> {
  VideoPlayerController? _video;
  bool _failed = false;
  bool _muted = true;

  PromoPopup get popup => widget.popup;

  @override
  void initState() {
    super.initState();
    if (popup.mediaType == PromoMediaType.video) _startVideo();
  }

  Future<void> _startVideo() async {
    final uri = Uri.parse(popup.mediaUrl);
    final c = widget.videoFactory?.call(uri) ?? VideoPlayerController.networkUrl(uri);
    _video = c;
    try {
      await c.initialize();
      // Départ sans son, en boucle ; le client peut activer le son.
      await c.setVolume(0);
      await c.setLooping(true);
      await c.play();
      if (!mounted) return;
      setState(() {});
      widget.onReady?.call();
    } catch (_) {
      if (mounted) _fail();
    }
  }

  void _fail() {
    // Média illisible : on ferme sans bruit, rien n'est compté comme vu.
    _failed = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.of(context).maybePop(false);
    });
    setState(() {});
  }

  @override
  void dispose() {
    _video?.pause();
    _video?.dispose();
    super.dispose();
  }

  void _close() => Navigator.of(context).pop(false);

  Widget _media() {
    if (popup.mediaType == PromoMediaType.image) {
      return Image(
        image: (PromoPopups.debugImageFactory ?? NetworkImage.new)(popup.mediaUrl),
        fit: BoxFit.contain,
        semanticLabel: popup.title,
        frameBuilder: (context, child, frame, sync) {
          if (frame != null || sync) {
            WidgetsBinding.instance.addPostFrameCallback((_) => widget.onReady?.call());
          }
          return child;
        },
        loadingBuilder: (context, child, progress) => progress == null
            ? child
            : const SizedBox(height: 220, child: Center(child: CircularProgressIndicator(strokeWidth: 3, color: AzColors.blue700))),
        errorBuilder: (context, error, stack) {
          if (!_failed) WidgetsBinding.instance.addPostFrameCallback((_) => mounted ? _fail() : null);
          return const SizedBox(height: 220);
        },
      );
    }
    final c = _video;
    if (c == null || !c.value.isInitialized) {
      return SizedBox(
        height: 220,
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (popup.posterUrl != null) Image.network(popup.posterUrl!, fit: BoxFit.cover, errorBuilder: (_, __, ___) => const SizedBox.shrink()),
            const Center(child: CircularProgressIndicator(strokeWidth: 3, color: AzColors.blue700)),
          ],
        ),
      );
    }
    return Stack(
      alignment: Alignment.bottomRight,
      children: [
        AspectRatio(aspectRatio: c.value.aspectRatio, child: VideoPlayer(c)),
        Padding(
          padding: const EdgeInsets.all(8),
          child: Material(
            color: const Color(0x99000000),
            shape: const CircleBorder(),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () {
                setState(() => _muted = !_muted);
                c.setVolume(_muted ? 0 : 1);
              },
              child: SizedBox(
                width: 44,
                height: 44,
                child: Semantics(
                  button: true,
                  label: _muted ? 'Activer le son' : 'Couper le son',
                  child: Icon(_muted ? Icons.volume_off_rounded : Icons.volume_up_rounded, color: Colors.white, size: 22),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final mq = MediaQuery.of(context);
    return SafeArea(
      child: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: 380, maxHeight: mq.size.height),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Align(
                  alignment: Alignment.centerRight,
                  child: Tooltip(
                    message: 'Fermer',
                    child: Semantics(
                      button: true,
                      label: 'Fermer',
                      excludeSemantics: true,
                      child: Material(
                        color: Colors.white,
                        shape: const CircleBorder(),
                        clipBehavior: Clip.antiAlias,
                        child: InkWell(
                          onTap: _close,
                          child: const SizedBox(width: 44, height: 44, child: Icon(Icons.close_rounded, color: AzColors.ink, size: 24)),
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 10),
                Material(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(26),
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      ConstrainedBox(constraints: BoxConstraints(maxHeight: mq.size.height * 0.62), child: _media()),
                      if (popup.hasAction)
                        Padding(
                          padding: const EdgeInsets.all(14),
                          child: AzButton(popup.linkLabel!, variant: AzButtonVariant.accent, height: 50, radius: 16, onPressed: () => Navigator.of(context).pop(true)),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
