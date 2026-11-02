package ci.assurzen.controller;

import ci.assurzen.dto.CrossSellRuleConditionRequest;
import ci.assurzen.dto.CrossSellRuleConditionResponse;
import ci.assurzen.dto.CrossSellRuleRequest;
import ci.assurzen.dto.CrossSellRuleResponse;
import ci.assurzen.services.CrossSellRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

import java.util.List;

@RestController
@RequestMapping("${env.ws}cross-sell/regles")
@Tag(name = "Vente Croisee - Regles", description = "Moteur de regles configurable pour la detection d'opportunites de vente croisee")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CrossSellRuleController {

    private final CrossSellRuleService crossSellRuleService;

    @GetMapping
    @Operation(operationId = "listCrossSellRules", summary = "Lister les regles de vente croisee (optionnellement filtrees par etat actif/inactif)")
    public ResponseEntity<List<CrossSellRuleResponse>> list(@RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(crossSellRuleService.list(actif));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCrossSellRule", summary = "Detail d'une regle")
    public ResponseEntity<CrossSellRuleResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(crossSellRuleService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createCrossSellRule", summary = "Creer une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellRuleResponse> create(@Valid @RequestBody CrossSellRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crossSellRuleService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateCrossSellRule", summary = "Mettre a jour une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellRuleResponse> update(@PathVariable Long id, @Valid @RequestBody CrossSellRuleRequest request) {
        return ResponseEntity.ok(crossSellRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteCrossSellRule", summary = "Desactiver une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        crossSellRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dupliquer")
    @Operation(operationId = "dupliquerCrossSellRule", summary = "Dupliquer une regle (et ses conditions) en brouillon inactif (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellRuleResponse> dupliquer(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crossSellRuleService.dupliquer(id));
    }

    @GetMapping("/{id}/conditions")
    @Operation(operationId = "listCrossSellRuleConditions", summary = "Lister les conditions d'une regle")
    public ResponseEntity<List<CrossSellRuleConditionResponse>> conditions(@PathVariable Long id) {
        return ResponseEntity.ok(crossSellRuleService.listConditions(id));
    }

    @PostMapping("/{id}/conditions")
    @Operation(operationId = "addCrossSellRuleCondition", summary = "Ajouter une condition a une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellRuleConditionResponse> ajouterCondition(@PathVariable Long id, @Valid @RequestBody CrossSellRuleConditionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(crossSellRuleService.ajouterCondition(id, request));
    }

    @DeleteMapping("/{id}/conditions/{conditionId}")
    @Operation(operationId = "deleteCrossSellRuleCondition", summary = "Retirer une condition d'une regle (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> supprimerCondition(@PathVariable Long id, @PathVariable Long conditionId) {
        crossSellRuleService.supprimerCondition(id, conditionId);
        return ResponseEntity.noContent().build();
    }
}
