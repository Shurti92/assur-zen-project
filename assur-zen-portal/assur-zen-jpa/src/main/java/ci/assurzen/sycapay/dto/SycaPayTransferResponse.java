package ci.assurzen.sycapay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reponse brute de POST /checktransferwave. Tous les champs sont nullables : les exemples reels de la
 * collection Postman montrent des reponses d'echec sans "status" ni "amount" (ex. code=-22
 * "LES DONNEES NE SONT PAS AUTHENTIQUES"). IMPORTANT : code=0 n'est PAS une confirmation finale du
 * virement, seulement un accusé de reception - status peut valoir "pending" (traitement asynchrone
 * cote SycaPay) aussi bien que "success". Le statut definitif se confirme via getStatusByOrderID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SycaPayTransferResponse(
        Integer code,
        String status,
        String description,
        String amount,
        String currency,
        String referencetransfer,
        @JsonProperty("orderID") String orderId,
        String destinataire,
        String provider
) {
    public boolean isAccepted() {
        return code != null && code == 0;
    }

    public boolean isConfirmedSuccess() {
        return isAccepted() && "success".equalsIgnoreCase(status);
    }

    public boolean isAsyncPending() {
        return isAccepted() && "pending".equalsIgnoreCase(status);
    }
}
