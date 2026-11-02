package ci.assurzen.webpush.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PushSubscription.toJSON() du navigateur (POST /notifications/subscriptions). Aucun userId : le
 * backend prend toujours l'utilisateur authentifie (JWT). */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2048) @Pattern(regexp = "^https://.+", message = "l'endpoint doit etre une URL https") String endpoint,
        Long expirationTime,
        @NotNull @Valid Keys keys
) {
    public record Keys(
            @NotBlank @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9_-]+={0,2}$") String p256dh,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+={0,2}$") String auth
    ) {
    }
}
