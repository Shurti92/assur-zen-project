package ci.assurzen.controller;

import ci.assurzen.services.UserActionTraiteeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Liste "A traiter" de l'Espace Partenaire (2026-10-03) : memorise les actions que l'utilisateur
 * connecte a marquees comme traitees. Aucun id dans l'URL, tout est deduit du JWT.
 */
@RestController
@RequestMapping("${env.ws}me/actions")
@Tag(name = "Actions a traiter", description = "Actions de la liste A traiter marquees comme traitees par l'utilisateur connecte")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserActionTraiteeController {

    private final UserActionTraiteeService service;

    @GetMapping("/traitees")
    @Operation(operationId = "listMyHandledActions", summary = "Cles des actions que j'ai marquees comme traitees (les plus recentes d'abord)")
    public ResponseEntity<List<String>> lister() {
        return ResponseEntity.ok(service.mesActionsTraitees());
    }

    @PostMapping("/{cle}/traitee")
    @Operation(operationId = "markMyActionHandled", summary = "Marquer une action comme traitee (idempotent, 204)")
    public ResponseEntity<Void> marquer(@PathVariable String cle) {
        service.marquerTraitee(cle);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{cle}/traitee")
    @Operation(operationId = "unmarkMyActionHandled", summary = "Annuler le marquage d'une action (idempotent, 204)")
    public ResponseEntity<Void> annuler(@PathVariable String cle) {
        service.annuler(cle);
        return ResponseEntity.noContent().build();
    }
}
