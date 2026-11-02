package ci.assurzen.controller;

import ci.assurzen.dto.SupportContactResponse;
import ci.assurzen.services.SupportContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contact d'aide de l'Espace Partenaire (2026-10-04) - carte "Besoin d'aide ?" / "Votre gestionnaire". */
@RestController
@RequestMapping("${env.ws}me")
@Tag(name = "Contact support", description = "Contact d'aide affiche par l'Espace Partenaire")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SupportContactController {

    private final SupportContactService service;

    @GetMapping("/support-contact")
    @Operation(operationId = "getMySupportContact",
            summary = "Contact d'aide de l'utilisateur connecte (contact support general regle en back-office : cles SUPPORT_*) ; champs null si non renseignes")
    public ResponseEntity<SupportContactResponse> contact() {
        return ResponseEntity.ok(service.contact());
    }
}
