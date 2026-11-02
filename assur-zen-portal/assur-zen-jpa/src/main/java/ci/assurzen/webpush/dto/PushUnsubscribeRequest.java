package ci.assurzen.webpush.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DELETE /notifications/subscriptions : desactive l'abonnement de CE navigateur, s'il appartient a
 * l'utilisateur authentifie. */
public record PushUnsubscribeRequest(@NotBlank @Size(max = 2048) String endpoint) {
}
