package ci.assurzen.controller;

import ci.assurzen.dto.InsuredRegistrationRequest;
import ci.assurzen.dto.InsuredRegistrationResponse;
import ci.assurzen.services.InsuredRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}souscriptions")
@Tag(name = "Souscriptions", description = "Enregistrement d'un assure avec ses contrats, et declenchement des SMS (bienvenue + rappels d'echeance)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class InsuredRegistrationController {

    private final InsuredRegistrationService service;

    @PostMapping
    @Operation(operationId = "registerInsuredWithContracts",
            summary = "Enregistrer un assure et ses contrats en une seule operation, avec envoi du SMS de bienvenue et planification des rappels d'echeance")
    public ResponseEntity<InsuredRegistrationResponse> register(@Valid @RequestBody InsuredRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }
}
