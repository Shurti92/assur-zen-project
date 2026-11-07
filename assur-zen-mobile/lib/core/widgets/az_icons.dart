import 'package:flutter/material.dart';

import '../../data/models/models.dart';
import '../theme/az_colors.dart';

/// Icône associée à un produit (Material Symbols, livrées avec Flutter).
IconData productIcon(String productId, {String? code}) {
  // Le back-office choisit un code libre ; un code inconnu retombe sur l'icône de l'identifiant.
  final byCode = switch (code) {
    'car' => Icons.directions_car_outlined,
    'moto' => Icons.two_wheeler_outlined,
    'taxi' => Icons.local_taxi_outlined,
    'health' => Icons.health_and_safety_outlined,
    'home' => Icons.home_outlined,
    'plane' => Icons.luggage_outlined,
    _ => null,
  };
  return byCode ?? _productIconById(productId);
}

IconData _productIconById(String productId) => switch (productId) {
      'auto' => Icons.directions_car_outlined,
      'moto' => Icons.two_wheeler_outlined,
      'vtc' || 'automobile-vtc' => Icons.local_taxi_outlined,
      'sante' => Icons.health_and_safety_outlined,
      'vie' => Icons.volunteer_activism_outlined,
      'habitation' => Icons.home_outlined,
      'voyage' => Icons.luggage_outlined,
      'scolaire' => Icons.school_outlined,
      'entreprise' => Icons.business_center_outlined,
      _ => Icons.shield_outlined,
    };

/// Teinte de fond associée à un produit.
Color productTint(String productId) => switch (productId) {
      'auto' || 'habitation' => AzColors.tintBlue,
      'moto' || 'voyage' => AzColors.tintSky,
      'vtc' || 'automobile-vtc' || 'scolaire' => AzColors.tintOrange,
      'sante' => AzColors.tintRose,
      'vie' => AzColors.tintLilac,
      _ => AzColors.tintGrey,
    };

IconData notificationIcon(NotificationCategory c) => switch (c) {
      NotificationCategory.echeance => Icons.schedule_outlined,
      NotificationCategory.souscription => Icons.verified_outlined,
      NotificationCategory.paiement => Icons.account_balance_wallet_outlined,
      NotificationCategory.sinistre => Icons.report_outlined,
      NotificationCategory.document => Icons.description_outlined,
      NotificationCategory.service => Icons.info_outline,
      NotificationCategory.loyalty => Icons.stars_outlined,
    };

/// Couleurs (texte, fond) d’une catégorie de notification.
(Color, Color) notificationColors(NotificationCategory c) => switch (c) {
      NotificationCategory.echeance => (AzColors.warning, AzColors.warningBg),
      NotificationCategory.souscription => (AzColors.blue700, AzColors.blue50),
      NotificationCategory.paiement => (AzColors.success, AzColors.successBg),
      NotificationCategory.sinistre => (AzColors.info, AzColors.infoBg),
      NotificationCategory.document => (AzColors.blue700, AzColors.blue50),
      NotificationCategory.service => (AzColors.ink2, AzColors.neutralBg),
      NotificationCategory.loyalty => (AzColors.blue700, AzColors.blue50),
    };
