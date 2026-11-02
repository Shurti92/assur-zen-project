package ci.assurzen.sycapay.dto;

/**
 * Corps JSON envoye a POST /checktransferwave. Noms de composants alignes tels quels sur les cles
 * attendues par SycaPay (verifie PDF + Postman) pour que la serialisation Jackson par defaut
 * (un champ Java "orderid" -&gt; la cle JSON "orderid") reproduise exactement le contrat reel, sans
 * mapping manuel source d'erreur.
 */
public record SycaPayTransferRequest(
        String orderid,
        String typetransfer,
        String orderparty,
        String connectuser,
        String connecteduser,
        String recipientident,
        String amount,
        String currency,
        String language,
        String urlcancel,
        String urlsuccess,
        String recipientmobile,
        String provider,
        String commentaire
) {
}
