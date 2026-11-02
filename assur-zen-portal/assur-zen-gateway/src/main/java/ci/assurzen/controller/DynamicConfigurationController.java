package ci.assurzen.controller;

import ci.assurzen.dto.DynamicConfigurationRequest;
import ci.assurzen.dto.DynamicConfigurationResponse;
import ci.assurzen.dto.HistoriqueModificationResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.ConfigCategory;
import ci.assurzen.entities.ConfigEnvironment;
import ci.assurzen.entities.ConfigValueType;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.HistoriqueModificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dynamic Configuration Engine : parametres metier/techniques modifiables sans redeploiement.
 * Reserve aux administrateurs (${env.ws}configurations, hasRole("ADMIN") dans SecurityConfig).
 */
@RestController
@RequestMapping("${env.ws}configurations")
@Tag(name = "Configurations", description = "Parametres dynamiques (modifiables en base, caches, sans redemarrage)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DynamicConfigurationController {

    private final DynamicConfigurationService dynamicConfigurationService;
    private final HistoriqueModificationService historiqueModificationService;

    @GetMapping
    @Operation(operationId = "listConfigurations", summary = "Rechercher les configurations (pagine, filtrable)")
    public ResponseEntity<PageResponse<DynamicConfigurationResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) ConfigCategory category,
            @RequestParam(required = false) ConfigValueType valueType,
            @RequestParam(required = false) ConfigEnvironment environment,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean editable,
            @RequestParam(required = false) Boolean sensitive,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(dynamicConfigurationService.search(q, module, category, valueType, environment, active, editable, sensitive, page, size));
    }

    @GetMapping("/categories")
    @Operation(operationId = "listConfigurationCategories", summary = "Lister les categories disponibles")
    public ResponseEntity<List<ConfigCategory>> categories() {
        return ResponseEntity.ok(dynamicConfigurationService.categories());
    }

    @GetMapping("/modules")
    @Operation(operationId = "listConfigurationModules", summary = "Lister les modules effectivement utilises par au moins une configuration")
    public ResponseEntity<List<String>> modules() {
        return ResponseEntity.ok(dynamicConfigurationService.modules());
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refreshAllConfigurations", summary = "Vider entierement le cache (toutes les configurations rechargees a la prochaine lecture)")
    public ResponseEntity<Void> refreshAll() {
        dynamicConfigurationService.refreshAll();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{key}")
    @Operation(operationId = "getConfiguration", summary = "Detail d'une configuration")
    public ResponseEntity<DynamicConfigurationResponse> get(@PathVariable String key) {
        return ResponseEntity.ok(dynamicConfigurationService.get(key));
    }

    @PostMapping
    @Operation(operationId = "createConfiguration", summary = "Creer une configuration")
    public ResponseEntity<DynamicConfigurationResponse> create(@Valid @RequestBody DynamicConfigurationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dynamicConfigurationService.create(request));
    }

    @PutMapping("/{key}")
    @Operation(operationId = "updateConfiguration", summary = "Mettre a jour la valeur/les metadonnees d'une configuration (invalide et recharge le cache immediatement)")
    public ResponseEntity<DynamicConfigurationResponse> update(@PathVariable String key, @Valid @RequestBody DynamicConfigurationRequest request) {
        return ResponseEntity.ok(dynamicConfigurationService.update(key, request));
    }

    @DeleteMapping("/{key}")
    @Operation(operationId = "deleteConfiguration", summary = "Supprimer une configuration")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        dynamicConfigurationService.delete(key);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{key}/activate")
    @Operation(operationId = "activateConfiguration", summary = "Activer une configuration")
    public ResponseEntity<DynamicConfigurationResponse> activate(@PathVariable String key) {
        return ResponseEntity.ok(dynamicConfigurationService.activate(key));
    }

    @PatchMapping("/{key}/deactivate")
    @Operation(operationId = "deactivateConfiguration", summary = "Desactiver une configuration (les lectures retombent sur default_value)")
    public ResponseEntity<DynamicConfigurationResponse> deactivate(@PathVariable String key) {
        return ResponseEntity.ok(dynamicConfigurationService.deactivate(key));
    }

    @PostMapping("/{key}/refresh")
    @Operation(operationId = "refreshConfiguration", summary = "Recharger immediatement une configuration depuis la base (invalide son entree de cache)")
    public ResponseEntity<DynamicConfigurationResponse> refresh(@PathVariable String key) {
        return ResponseEntity.ok(dynamicConfigurationService.refresh(key));
    }

    @GetMapping("/{key}/history")
    @Operation(operationId = "getConfigurationHistory", summary = "Historique complet d'une configuration (reutilise l'Audit Trail Engine)")
    public ResponseEntity<List<HistoriqueModificationResponse>> history(@PathVariable String key) {
        return ResponseEntity.ok(historiqueModificationService.getByEntity("DynamicConfiguration", key));
    }

    @PostMapping("/{key}/restore/{historiqueId}")
    @Operation(operationId = "restoreConfiguration", summary = "Restaurer une configuration a une valeur precedente (depuis une entree d'historique)")
    public ResponseEntity<DynamicConfigurationResponse> restore(@PathVariable String key, @PathVariable Long historiqueId) {
        return ResponseEntity.ok(dynamicConfigurationService.restore(key, historiqueId));
    }
}
