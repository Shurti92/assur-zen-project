package ci.assurzen.webpush.dto;

import java.util.Map;

/**
 * Contenu d'une notification push (JSON lu par le service worker). Volontairement court et sans donnee
 * sensible (pas de montant, decision du 2026-09-26) : le detail est dans l'application. data : type,
 * notificationId, route.
 */
public record WebPushMessage(String title, String body, Map<String, String> data) {
}
