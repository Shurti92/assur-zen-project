package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.TraductionRequest;
import ci.assurzen.dto.TraductionResponse;
import ci.assurzen.dto.TraductionUpdateRequest;
import ci.assurzen.services.I18nService;
import ci.assurzen.services.TraductionEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Dictionnaire de traductions i18n runtime pour le frontend (Transloco). Voir
 * docs/api-contracts/i18n.md du repo assur-zen-core pour le contrat complet.
 */
@RestController
@RequestMapping("${env.ws}i18n")
@Tag(name = "I18n", description = "Dictionnaire de traductions runtime (public) et administration des entrees (protegee)")
@RequiredArgsConstructor
public class I18nController {

    private final I18nService i18nService;
    private final TraductionEntryService traductionEntryService;

    @GetMapping("/version")
    @Operation(operationId = "getI18nVersion", summary = "Date de derniere modification du dictionnaire, par langue (pour le cache frontend)")
    @SecurityRequirements
    public ResponseEntity<Map<String, Instant>> version() {
        return ResponseEntity.ok(i18nService.version());
    }

    @GetMapping("/entries")
    @Operation(operationId = "listTraductionEntries", summary = "Lister/rechercher les entrees du dictionnaire (paginé, reserve aux administrateurs/marketing/communication)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PageResponse<TraductionResponse>> listEntries(
            @RequestParam(required = false) String langueCode,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(traductionEntryService.list(langueCode, scope, page, size));
    }

    @PostMapping("/entries")
    @Operation(operationId = "createTraductionEntry", summary = "Ajouter une entree au dictionnaire (reserve aux administrateurs/marketing/communication)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TraductionResponse> createEntry(@Valid @RequestBody TraductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(traductionEntryService.create(request));
    }

    @PutMapping("/entries/{id}")
    @Operation(operationId = "updateTraductionEntry", summary = "Mettre a jour la valeur d'une entree (reserve aux administrateurs/marketing/communication)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TraductionResponse> updateEntry(@PathVariable Long id, @Valid @RequestBody TraductionUpdateRequest request) {
        return ResponseEntity.ok(traductionEntryService.update(id, request));
    }

    @DeleteMapping("/entries/{id}")
    @Operation(operationId = "deleteTraductionEntry", summary = "Supprimer une entree (reserve aux administrateurs/marketing/communication)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id) {
        traductionEntryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{langueCode}")
    @Operation(operationId = "getI18nDictionnaire", summary = "Dictionnaire de traductions plat (cle -> valeur) pour une langue, filtrable par scope")
    @SecurityRequirements
    public ResponseEntity<Map<String, String>> dictionnaire(@PathVariable String langueCode,
                                                              @RequestParam(required = false) String scope) {
        return ResponseEntity.ok(i18nService.dictionnaire(langueCode, scope));
    }
}
