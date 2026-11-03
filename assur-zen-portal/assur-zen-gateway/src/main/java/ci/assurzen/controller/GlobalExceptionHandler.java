package ci.assurzen.controller;

import ci.assurzen.dto.DailyLimitReachedErrorResponse;
import ci.assurzen.dto.ErrorResponse;
import ci.assurzen.exception.CommercialPaymentRequestException;
import ci.assurzen.exception.DailyPartialPaymentLimitReachedException;
import ci.assurzen.exception.PartialPaymentAmountExceedsCapException;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.exception.SycaPayException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * server.error.include-message est a "never" (audit de securite pre-production du 2026-08-23 :
 * "always" faisait fuiter le message brut de N'IMPORTE QUELLE exception non geree - y compris des
 * messages internes Hibernate - a n'importe quel client, dans TOUS les environnements). Consequence :
 * ce handler doit desormais construire EXPLICITEMENT la reponse pour les deux categories de messages
 * qui DOIVENT rester visibles au client (volontaires, jamais des details internes) :
 * - ResponseStatusException : le "reason" est toujours un texte metier ecrit a la main par un
 *   developpeur (ex. "Contrat introuvable: 42"), jamais un message d'exception brut - safe a exposer.
 * - le catch-all Exception : PAS safe a exposer, remplace systematiquement par un message generique.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Limite spring.servlet.multipart.max-file-size depassee (2026-10-05) : message explicite plutot que
     * le "requete multipart invalide" generique ci-dessous (MaxUploadSizeExceededException en herite). */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> fichierTropVolumineux(org.springframework.web.multipart.MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("FICHIER_TROP_VOLUMINEUX",
                        "Fichier trop volumineux (" + maxFileSize.toMegabytes() + " Mo maximum)", Instant.now()));
    }

    @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:5MB}")
    private org.springframework.util.unit.DataSize maxFileSize = org.springframework.util.unit.DataSize.ofMegabytes(5);

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> multipartError(MultipartException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_MULTIPART_REQUEST", "Fichier manquant ou requete multipart invalide", Instant.now()));
    }

    /** Meme classe de regression que NoResourceFoundException/HttpRequestMethodNotSupportedException :
     * une requete multipart bien formee mais sans la partie nommee attendue (ex. POST .../pieces-jointes
     * sans "file") tombait dans le catch-all et renvoyait 500 au lieu du 400 standard de Spring. Trouve
     * le 2026-09-09 en testant tous les endpoints d'upload de fichiers du projet. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> partieMultipartManquante(MissingServletRequestPartException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_MULTIPART_PART",
                        "Partie manquante dans la requete multipart : " + exception.getRequestPartName(), Instant.now()));
    }

    @ExceptionHandler(DailyPartialPaymentLimitReachedException.class)
    public ResponseEntity<DailyLimitReachedErrorResponse> dailyPartialPaymentLimitReached(DailyPartialPaymentLimitReachedException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new DailyLimitReachedErrorResponse("DAILY_LIMIT_REACHED", exception.getMessage(), exception.getNextAvailableAt()));
    }

    /** Integration SycaPay (2026-09-23) : paiement deja en cours/effectue pour ce contrat - levee par
     * POST /commissions/pay (paiement manuel d'un contrat deja pris en charge par SycaPay). Message
     * transmis tel quel, affiche par le frontend : TOUJOURS un texte metier redige a la main citant le
     * contrat (meme regle que les "reason" de ResponseStatusException), jamais un detail technique. */
    /** Demande de paiement de commission d'un commercial (2026-09-25) : code et statut portes par
     * l'exception (NOTHING_TO_PAY 409, AMOUNT_EXCEEDS_BALANCE / INVALID_AMOUNT 422...), message
     * francais affiche tel quel par le frontend. */
    @ExceptionHandler(CommercialPaymentRequestException.class)
    public ResponseEntity<ErrorResponse> commercialPaymentRequest(CommercialPaymentRequestException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getCode(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(SycaPayDuplicatePaymentException.class)
    public ResponseEntity<ErrorResponse> sycaPayDuplicatePayment(SycaPayDuplicatePaymentException exception) {
        log.warn("[SYCAPAY] paiement en double rejete : {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("SYCAPAY_DUPLICATE_PAYMENT", exception.getMessage(), Instant.now()));
    }

    /** Integration SycaPay (2026-09-23) : echec du fournisseur externe (reseau, authentification,
     * reponse illisible, integration desactivee ou credentials incomplets) sur /sycapay/balance,
     * /sycapay/transactions/{id}/reconcile... Avant ce handler, ces cas tombaient dans le catch-all et
     * renvoyaient 500 "Une erreur inattendue" - le frontend ne pouvait pas distinguer une panne SycaPay
     * d'un bug AssurZen. 502 + code dedie, message GENERIQUE (le message brut peut decrire la
     * configuration interne), detail uniquement dans les logs serveur. */
    @ExceptionHandler(SycaPayException.class)
    public ResponseEntity<ErrorResponse> sycaPayIndisponible(SycaPayException exception) {
        log.error("[SYCAPAY] echec de l'appel au fournisseur : {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("SYCAPAY_UNAVAILABLE",
                        "Service de paiement SycaPay indisponible, veuillez reessayer plus tard", Instant.now()));
    }

    @ExceptionHandler(PartialPaymentAmountExceedsCapException.class)
    public ResponseEntity<ErrorResponse> partialPaymentAmountExceedsCap(PartialPaymentAmountExceedsCapException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("AMOUNT_EXCEEDS_CAP", exception.getMessage(), Instant.now()));
    }

    /** Modification concurrente de la meme ligne (@Version) - ex. deux PUT /fleets/{id} avec la
     * meme version obsolete. Jamais le message brut (peut contenir un nom d'entite/table interne). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> verrouOptimiste(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONCURRENT_MODIFICATION", "Cette ressource a ete modifiee entre-temps, veuillez recharger et reessayer", Instant.now()));
    }

    /**
     * Filet de securite pour toute contrainte SQL (FK ou unicite) non anticipee par un controle
     * metier explicite en amont - avant ce handler, tombait dans le catch-all Exception generique
     * et remontait en 500 (bug reel constate le 2026-09-14 : DELETE /contracts/{id} sur un contrat
     * ayant des paiements/avenants/sinistres/ecritures comptables/commissions/mouvements de flotte
     * lies - toutes des FK sans cascade en base, cf. ContractsService.delete()). Une contrainte SQL
     * violee signifie quasi-toujours "operation en conflit avec des donnees existantes" - 409 est le
     * bon statut, jamais le 500 par defaut de la BDD. Jamais le message brut (peut contenir un nom
     * de table/contrainte interne) - message generique suffisant, log cote serveur pour le detail.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> contrainteBaseViolee(DataIntegrityViolationException exception) {
        log.warn("Contrainte SQL violee : {}", exception.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DATA_INTEGRITY_CONFLICT",
                        "Operation impossible : cette ressource est encore referencee par d'autres donnees, ou entre en conflit avec une valeur existante",
                        Instant.now()));
    }

    /** Couvre les centaines de "throw new ResponseStatusException(HttpStatus.X, "raison metier")"
     * deja presents dans tous les services - reste de la responsabilite de qui les ecrit de ne jamais
     * y mettre un detail interne, mais c'est deja la convention etablie dans tout ce projet. */
    /** Fichier joint refuse (2026-10-05) : code stable plutot que "400 BAD_REQUEST". */
    @ExceptionHandler(ci.assurzen.exception.DocumentTypeNonAutoriseException.class)
    public ResponseEntity<ErrorResponse> documentTypeNonAutorise(ci.assurzen.exception.DocumentTypeNonAutoriseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ci.assurzen.exception.DocumentTypeNonAutoriseException.CODE, exception.getReason(), Instant.now()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> responseStatusException(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() != null ? exception.getReason() : status.toString();
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.toString(), message, Instant.now()));
    }

    /** Regression du 2026-08-23 decouverte le meme jour que celle de NoResourceFoundException, par un
     * test manuel du compte admin (GET /dashboard/kpis sans le parametre requis "period", POST
     * /partenaires avec un corps vide) : AUCUNE des exceptions "erreur client attendue" ci-dessous
     * n'avait jamais ete geree explicitement dans ce projet. Avant le catch-all Exception, Spring les
     * traitait automatiquement en 400/405 avec leurs propres messages. Depuis son ajout, TOUTES
     * tombaient dans le catch-all et renvoyaient 500 - sur N'IMPORTE QUEL endpoint de toute l'appli
     * utilisant @Valid, un @RequestParam requis, ou un type de parametre incorrect. Regression bien
     * plus large que celle de Swagger (qui ne touchait qu'une poignee d'URLs invalides). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validationEchouee(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(erreur -> erreur.getField() + ": " + erreur.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String champ = exception.getBindingResult().getFieldErrors().stream()
                .map(erreur -> erreur.getField()).findFirst().orElse(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", message.isEmpty() ? "Requete invalide" : message, Instant.now(), champ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> contrainteViolee(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", message.isEmpty() ? "Requete invalide" : message, Instant.now()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validationParametreEchouee(HandlerMethodValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", "Requete invalide", Instant.now()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametreManquant(MissingServletRequestParameterException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_PARAMETER",
                        "Parametre requis manquant : " + exception.getParameterName(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametreInvalide(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PARAMETER",
                        "Valeur invalide pour le parametre : " + exception.getName(), Instant.now()));
    }

    /** Jamais le message brut (peut contenir des details Jackson internes) - juste un signal generique
     * "corps de requete illisible", suffisant pour un client mal forme. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> corpsIllisible(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MALFORMED_REQUEST_BODY", "Corps de requete illisible ou JSON invalide", Instant.now()));
    }

    /** Regression du 2026-08-23 : avant l'ajout du catch-all Exception ci-dessous, une URL sans
     * route correspondante (ex. /v3/api-docs quand springdoc est desactive, faute de frappe, ancien
     * endpoint supprime) tombait sur le comportement 404 par defaut de Spring. Depuis, elle etait
     * interceptee par le catch-all et renvoyait 500 - regression decouverte en testant la desactivation
     * de Swagger en profil prod (point n°6 de l'audit). NoResourceFoundException doit rester un 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> ressourceIntrouvable(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Ressource introuvable", Instant.now()));
    }

    /** Meme classe de regression que NoResourceFoundException ci-dessus, trouvee le 2026-09-09 en
     * testant GET /users/{id} (route inexistante - seuls PUT/DELETE/PATCH/GET-sous-chemin existent sur
     * ce pattern) : une URL qui matche un pattern existant mais avec un verbe HTTP non supporte sur
     * cette route tombait dans le catch-all et renvoyait 500 au lieu du 405 Method Not Allowed standard
     * de Spring. Bug transversal (n'importe quelle route, pas specifique a /users). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodeNonSupportee(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse("METHOD_NOT_ALLOWED", "Methode HTTP non supportee pour cette ressource", Instant.now()));
    }

    /** Meme audit du 2026-09-09 : Content-Type non supporte sur le corps de la requete (ex. POST
     * /partenaires avec Content-Type: text/plain) tombait aussi dans le catch-all - 500 au lieu du 415
     * standard de Spring. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> typeContenuNonSupporte(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "Type de contenu non supporte pour cette requete", Instant.now()));
    }

    /** Filet de securite : toute exception non prevue (Hibernate, NPE, etc.) - jamais son message
     * brut au client, seulement logue cote serveur pour investigation. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> exceptionInattendue(Exception exception) {
        log.error("Exception non geree remontee jusqu'au client", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Une erreur inattendue est survenue", Instant.now()));
    }
}
