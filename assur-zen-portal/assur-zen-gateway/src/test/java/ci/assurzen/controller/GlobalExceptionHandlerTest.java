package ci.assurzen.controller;

import ci.assurzen.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression du 2026-08-23 (decouverte en 2 temps le meme jour) : avant l'ajout du catch-all Exception
 * dans GlobalExceptionHandler pendant l'audit de securite, Spring gerait automatiquement les erreurs
 * "client attendues" (404 sur URL invalide, 400 sur validation/parametre manquant/JSON illisible).
 * Depuis, TOUTES tombaient dans le catch-all et renvoyaient 500 - decouvert d'abord sur
 * NoResourceFoundException (test de la desactivation de Swagger en profil prod), puis sur les
 * exceptions de validation (test manuel du compte admin : GET /dashboard/kpis sans parametre requis,
 * POST /partenaires avec un corps vide).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ressourceIntrouvable_renvoie404PasDeMessageInterne() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "v3/api-docs", "No static resource v3/api-docs for request '/v3/api-docs'.");

        ResponseEntity<ErrorResponse> response = handler.ressourceIntrouvable(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
    }

    /** exceptionInattendue() logue volontairement l'exception en ERROR (cote serveur, pour
     * investigation) avant de renvoyer un message generique au client - comportement correct et
     * inchange, mais qui polluait la sortie de ce test avec une stack trace qui ressemble a un echec
     * alors que le test passe (Tests run: X, Failures: 0). Le logger Logback de la classe est mis en
     * sourdine pour la duree du test, jamais le comportement de production. */
    @Test
    void exceptionGenerique_renvoieToujours500SansExposerLeMessageBrut() {
        RuntimeException exception = new RuntimeException("detail Hibernate sensible");

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ch.qos.logback.classic.Level niveauInitial = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.OFF);
        ResponseEntity<ErrorResponse> response;
        try {
            response = handler.exceptionInattendue(exception);
        } finally {
            logger.setLevel(niveauInitial);
        }

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Une erreur inattendue est survenue", response.getBody().message());
    }

    @Test
    void validationEchouee_renvoie400AvecLesChampsEnFaute() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("partenaireRequest", "nom", "ne doit pas être vide")));
        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.validationEchouee(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
        assertEquals("nom: ne doit pas être vide", response.getBody().message());
    }

    @Test
    void parametreManquant_renvoie400AvecLeNomDuParametre() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("period", "DashboardPeriod");

        ResponseEntity<ErrorResponse> response = handler.parametreManquant(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Parametre requis manquant : period", response.getBody().message());
    }

    @Test
    void parametreInvalide_renvoie400() {
        MethodParameter parameter = mock(MethodParameter.class);
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter, new NumberFormatException());

        ResponseEntity<ErrorResponse> response = handler.parametreInvalide(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Valeur invalide pour le parametre : page", response.getBody().message());
    }

    @Test
    void corpsIllisible_renvoie400SansExposerLeMessageBrutJackson() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);

        ResponseEntity<ErrorResponse> response = handler.corpsIllisible(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Corps de requete illisible ou JSON invalide", response.getBody().message());
    }

    @Test
    void contrainteViolee_renvoie400() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("doit etre positif");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.contrainteViolee(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
    }

    /** Module Gestion de flotte (@Version sur Fleet/Vehicule/FleetVehicle) : deux modifications
     * concurrentes de la meme ligne doivent renvoyer 409, jamais 500, et jamais le message brut
     * (peut nommer l'entite/table interne en conflit). */
    @Test
    void verrouOptimiste_renvoie409PasDeMessageInterne() {
        OptimisticLockingFailureException exception = new OptimisticLockingFailureException("Row was updated or deleted by another transaction");

        ResponseEntity<ErrorResponse> response = handler.verrouOptimiste(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONCURRENT_MODIFICATION", response.getBody().code());
        assertEquals("Cette ressource a ete modifiee entre-temps, veuillez recharger et reessayer", response.getBody().message());
    }

    /** Bug reel du 2026-09-14 : DELETE /contracts/{id} sur un contrat ayant des rappels SMS
     * (NotificateurSms, depuis corrige a la source) ou tout autre enregistrement lie (paiements,
     * avenants, sinistres...) faisait echouer la suppression en DataIntegrityViolationException,
     * jamais gere explicitement avant ce handler - tombait dans le catch-all Exception generique
     * et remontait en 500 au lieu d'un 409 exploitable. Jamais le message brut (peut nommer la
     * table/contrainte SQL interne en cause). */
    @Test
    void contrainteBaseViolee_renvoie409PasDeMessageInterne() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement",
                new java.sql.SQLIntegrityConstraintViolationException(
                        "Cannot delete or update a parent row: a foreign key constraint fails (fk_notificateur_sms_contract)"));

        ResponseEntity<ErrorResponse> response = handler.contrainteBaseViolee(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_INTEGRITY_CONFLICT", response.getBody().code());
        assertEquals("Operation impossible : cette ressource est encore referencee par d'autres donnees, ou entre en conflit avec une valeur existante",
                response.getBody().message());
    }

    @Test
    void validationParametreEchouee_renvoie400() {
        HandlerMethodValidationException exception = mock(HandlerMethodValidationException.class);

        ResponseEntity<ErrorResponse> response = handler.validationParametreEchouee(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_FAILED", response.getBody().code());
    }

    // ---- Fichiers joints (2026-10-05) : codes d'erreur stables pour l'Espace Partenaire ----

    @Test
    void documentTypeNonAutorise_400CodeStable() {
        ResponseEntity<ErrorResponse> response = handler.documentTypeNonAutorise(
                new ci.assurzen.exception.DocumentTypeNonAutoriseException("Format de fichier non autorise"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("DOCUMENT_TYPE_NON_AUTORISE", response.getBody().code());
        assertEquals("Format de fichier non autorise", response.getBody().message());
    }

    @Test
    void fichierTropVolumineux_400CodeStableEtLimite() {
        ResponseEntity<ErrorResponse> response = handler.fichierTropVolumineux(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(5L * 1024 * 1024));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("FICHIER_TROP_VOLUMINEUX", response.getBody().code());
        assertEquals("Fichier trop volumineux (5 Mo maximum)", response.getBody().message());
    }
}
