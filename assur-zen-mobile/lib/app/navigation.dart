import 'package:flutter/material.dart';

/// Clé du navigateur racine, partagée pour ouvrir un écran hors d'un widget
/// (verrouillage, lien profond d'une notification push).
final GlobalKey<NavigatorState> appNavigatorKey = GlobalKey<NavigatorState>();
