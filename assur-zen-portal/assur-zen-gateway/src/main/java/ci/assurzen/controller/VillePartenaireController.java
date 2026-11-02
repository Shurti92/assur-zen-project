package ci.assurzen.controller;

import ci.assurzen.dto.VillePartenaireRequest;
import ci.assurzen.dto.VillePartenaireResponse;
import ci.assurzen.services.VillePartenaireService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}ville-partenaires")
@Tag(name = "Villes partenaires", description = "CRUD des villes partenaires")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class VillePartenaireController {

    private final VillePartenaireService service;

    @GetMapping
    @Operation(operationId = "listVillePartenaires", summary = "Lister les villes partenaires")
    public ResponseEntity<List<VillePartenaireResponse>> list() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getVillePartenaire", summary = "Consulter une ville partenaire")
    public ResponseEntity<VillePartenaireResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createVillePartenaire", summary = "Creer une ville partenaire")
    public ResponseEntity<VillePartenaireResponse> create(@Valid @RequestBody VillePartenaireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateVillePartenaire", summary = "Mettre a jour une ville partenaire")
    public ResponseEntity<VillePartenaireResponse> update(@PathVariable Long id, @Valid @RequestBody VillePartenaireRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteVillePartenaire", summary = "Supprimer une ville partenaire")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
