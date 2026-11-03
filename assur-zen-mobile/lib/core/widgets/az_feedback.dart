import 'package:flutter/material.dart';

import '../theme/az_colors.dart';
import '../theme/az_spacing.dart';
import '../theme/az_typography.dart';
import 'az_buttons.dart';

/// Message bref en bas d’écran (snackbar AssurZen).
void showAzSnack(BuildContext context, String message, {String? actionLabel, VoidCallback? onAction}) {
  final messenger = ScaffoldMessenger.maybeOf(context);
  if (messenger == null) return;
  messenger
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        backgroundColor: AzColors.ink,
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        duration: const Duration(seconds: 3),
        content: Text(message, style: AzText.bodyMedium.copyWith(color: Colors.white)),
        action: actionLabel == null
            ? null
            : SnackBarAction(label: actionLabel, textColor: const Color(0xFFFFB27A), onPressed: onAction ?? () {}),
      ),
    );
}

/// Dialogue de confirmation. Renvoie `true` si l’utilisateur confirme.
Future<bool> showAzConfirm(
  BuildContext context, {
  required String title,
  required String message,
  required String confirmLabel,
  String cancelLabel = 'Annuler',
  bool danger = false,
  IconData? icon,
}) async {
  final result = await showDialog<bool>(
    context: context,
    barrierColor: AzColors.scrim,
    builder: (context) => Dialog(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 28),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(22, 22, 22, 14),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (icon != null) ...[
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(color: danger ? AzColors.errorBg : AzColors.infoBg, borderRadius: BorderRadius.circular(16)),
                child: Icon(icon, color: danger ? AzColors.error : AzColors.info),
              ),
              const SizedBox(height: 14),
            ],
            Text(title, style: AzText.title.copyWith(fontSize: 19)),
            const SizedBox(height: 8),
            Text(message, style: AzText.bodyMedium.copyWith(color: AzColors.muted)),
            const SizedBox(height: 16),
            // Wrap : un libellé long passe à la ligne au lieu de déborder de la boîte.
            Wrap(
              alignment: WrapAlignment.end,
              spacing: 8,
              runSpacing: 8,
              children: [
                AzButton.ghost(cancelLabel, expand: false, height: 44, onPressed: () => Navigator.of(context).pop(false)),
                AzButton(
                  confirmLabel,
                  expand: false,
                  height: 44,
                  variant: danger ? AzButtonVariant.danger : AzButtonVariant.primary,
                  onPressed: () => Navigator.of(context).pop(true),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
  return result ?? false;
}

/// Feuille du bas (bottom sheet) avec poignée, coins arrondis et clavier géré.
Future<T?> showAzSheet<T>(BuildContext context, {required WidgetBuilder builder, String? title}) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.white,
    barrierColor: AzColors.scrim,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(AzRadius.sheet))),
    builder: (context) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(AzSpace.screen, 12, AzSpace.screen, AzSpace.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(color: AzColors.fieldBorder, borderRadius: BorderRadius.circular(2)),
                ),
              ),
              const SizedBox(height: 14),
              if (title != null) ...[
                Text(title, style: AzText.title),
                const SizedBox(height: 10),
              ],
              builder(context),
            ],
          ),
        ),
      ),
    ),
  );
}
