package ci.assurzen.sycapay.client;

import ci.assurzen.sycapay.dto.SycaPayAuthResponse;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayStatusResponse;
import ci.assurzen.sycapay.dto.SycaPayTransferRequest;
import ci.assurzen.sycapay.dto.SycaPayTransferResponse;

/**
 * Acces HTTP brut aux 4 APIs SycaPay (authentification, solde, virement Wave, statut). Aucune
 * logique metier commission ici (pas de notion de commission/commercial/contrat) - uniquement la
 * traduction requete/reponse HTTP <-> DTO, la construction des headers et de la signature
 * AUTHORIZATION. Consomme par les services du package sycapay.service, jamais directement par
 * CommercialCommissionService.
 */
public interface SycaPayClient {

    /** POST /authentification. Ne leve jamais d'exception sur un refus fonctionnel (code != 0) -
     * l'appelant lit SycaPayAuthResponse.isSuccess(). Leve SycaPayAuthenticationException en cas
     * d'erreur technique (reseau, timeout, reponse illisible). */
    SycaPayAuthResponse authenticate();

    /** POST /checkcashoutsolde.php. Leve SycaPayBalanceException en cas d'erreur technique. */
    SycaPayBalanceResponse checkBalance();

    /** POST /checktransferwave, avec le token deja obtenu par l'appelant (SycaPayAuthenticationService).
     * Ne leve PAS d'exception sur timeout/erreur reseau : retourne null pour signaler explicitement un
     * resultat INCONNU (cf. section 11 de la specification - jamais interpreter un timeout comme un
     * echec). Leve SycaPayPaymentException uniquement si l'appel n'a manifestement pas pu partir
     * (configuration invalide). */
    SycaPayTransferResponse transferWave(String token, SycaPayTransferRequest request);

    /** POST /getStatusByOrderID.php. orderId = notre transaction_reference (PAS le referencetransfer
     * SycaPay - verifie sur les exemples reels de la collection Postman). Retourne null sur
     * timeout/erreur reseau, comme transferWave. */
    SycaPayStatusResponse getStatus(String orderId);
}
