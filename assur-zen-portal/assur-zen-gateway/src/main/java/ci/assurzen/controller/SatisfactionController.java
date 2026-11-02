package ci.assurzen.controller;

import ci.assurzen.dto.EnqueteReponseRequest;
import ci.assurzen.dto.EnqueteSatisfactionRequest;
import ci.assurzen.dto.EnqueteSatisfactionResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.EnqueteContexte;
import ci.assurzen.entities.EnqueteStatut;
import ci.assurzen.services.SatisfactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}enquetes-satisfaction")
@Tag(name = "Satisfaction Client", description = "Envoi et suivi des enquetes de satisfaction (score NPS)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SatisfactionController {

    private final SatisfactionService satisfactionService;

    @GetMapping
    @Operation(operationId = "listEnquetesSatisfaction", summary = "Lister/rechercher les enquetes de satisfaction (paginé, filtrable par statut/contexte)")
    public ResponseEntity<PageResponse<EnqueteSatisfactionResponse>> list(
            @RequestParam(required = false) EnqueteStatut statut,
            @RequestParam(required = false) EnqueteContexte contexte,
            @RequestParam(required = false) Long insuredId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(satisfactionService.search(statut, contexte, insuredId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getEnqueteSatisfaction", summary = "Detail d'une enquete de satisfaction")
    public ResponseEntity<EnqueteSatisfactionResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(satisfactionService.get(id));
    }

    @PostMapping
    @Operation(operationId = "envoyerEnqueteSatisfaction", summary = "Envoyer une enquete de satisfaction a un assure")
    public ResponseEntity<EnqueteSatisfactionResponse> envoyer(@Valid @RequestBody EnqueteSatisfactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(satisfactionService.envoyer(request));
    }

    @PatchMapping("/{id}/reponse")
    @Operation(operationId = "repondreEnqueteSatisfaction", summary = "Enregistrer la reponse (score NPS + commentaire) a une enquete")
    public ResponseEntity<EnqueteSatisfactionResponse> repondre(@PathVariable Long id, @Valid @RequestBody EnqueteReponseRequest request) {
        return ResponseEntity.ok(satisfactionService.repondre(id, request));
    }
}
