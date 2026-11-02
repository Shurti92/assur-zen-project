package ci.assurzen.webpush.dto;

import java.time.Instant;

/** Abonnement enregistre - jamais les cles du navigateur en retour. */
public record PushSubscriptionResponse(Long id, boolean active, Instant createdAt, Instant updatedAt) {
}
