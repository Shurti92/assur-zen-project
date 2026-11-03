package ci.assurzen.controller;

import ci.assurzen.dto.MobileAuthDtos;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.MobileAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentification et inscription de l'application mobile - lot 1 (contrat valide le 2026-11-02, voir
 * docs/api-contracts/mobile-lot1-authentification.md). Login, refresh et logout restent sous /auth/.
 */
@RestController
@RequestMapping("${env.ws}auth/mobile")
@Tag(name = "Mobile - Authentification", description = "Inscription, verification du numero par SMS, PIN et suppression de compte (application mobile)")
@RequiredArgsConstructor
public class MobileAuthController {

    private final MobileAuthService service;

    @PostMapping("/otp/request")
    @Operation(operationId = "mobileOtpRequest", summary = "Demander un code SMS (REGISTER ou RESET_PIN) - 3 demandes/heure/numero ; 409 si le numero est deja inscrit (REGISTER) ; RESET_PIN sur numero inconnu : 204 sans SMS")
    @SecurityRequirements
    public ResponseEntity<Void> demanderCode(@Valid @RequestBody MobileAuthDtos.OtpRequest request) {
        service.demanderCode(request.phone(), request.purpose());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    @Operation(operationId = "mobileOtpVerify", summary = "Verifier le code SMS et obtenir le jeton de verification du numero (usage unique, 10 min)")
    @SecurityRequirements
    public ResponseEntity<MobileAuthDtos.OtpVerifyResponse> verifierCode(@Valid @RequestBody MobileAuthDtos.OtpVerifyRequest request) {
        return ResponseEntity.ok(service.verifierCode(request.phone(), request.code(), request.purpose()));
    }

    @PostMapping("/register")
    @Operation(operationId = "mobileRegister", summary = "Creer un compte client (role CLIENT) apres verification du numero ; rattache automatiquement les assures du back-office portant ce numero ; renvoie directement la session")
    @SecurityRequirements
    public ResponseEntity<MobileAuthDtos.RegisterResponse> inscrire(@Valid @RequestBody MobileAuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.inscrire(request));
    }

    @PostMapping("/pin/reset")
    @Operation(operationId = "mobilePinReset", summary = "Reinitialiser le PIN avec le code SMS (purpose RESET_PIN) - 204 aussi pour un numero inconnu ; revoque toutes les sessions")
    @SecurityRequirements
    public ResponseEntity<Void> reinitialiserPin(@Valid @RequestBody MobileAuthDtos.PinResetRequest request) {
        service.reinitialiserPin(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pin/verify")
    @Operation(operationId = "mobilePinVerify", summary = "Verifier le PIN du compte connecte sans creer de session ni envoyer d'OTP ; un echec compte dans le verrouillage (401 puis 423)")
    public ResponseEntity<Void> verifierPin(Authentication authentication, @Valid @RequestBody MobileAuthDtos.PinRequest request) {
        service.verifierPin(utilisateur(authentication).userId(), request.pin());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/delete-request")
    @Operation(operationId = "mobileDeleteRequest", summary = "Demander la suppression du compte (PIN requis) : demande enregistree pour traitement par un administrateur, connexions coupees ; idempotent")
    public ResponseEntity<MobileAuthDtos.DeleteRequestResponse> demanderSuppression(Authentication authentication,
                                                                                    @Valid @RequestBody MobileAuthDtos.PinRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.demanderSuppression(utilisateur(authentication).userId(), request.pin()));
    }

    private AuthenticatedUserPrincipal utilisateur(Authentication authentication) {
        return (AuthenticatedUserPrincipal) authentication.getPrincipal();
    }
}
