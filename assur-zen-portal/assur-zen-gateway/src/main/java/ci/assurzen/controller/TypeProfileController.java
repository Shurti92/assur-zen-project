package ci.assurzen.controller;

import ci.assurzen.dto.TypeProfileRequest;
import ci.assurzen.dto.TypeProfileResponse;
import ci.assurzen.services.TypeProfileService;
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
@RequestMapping("${env.ws}type-profiles")
@Tag(name = "Type profile", description = "Paramétrage des type de profile")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class TypeProfileController {

    private final TypeProfileService typeProfileService;

    @GetMapping
    @Operation(operationId = "listTypeProfiles", summary = "Lister les types de profil")
    public ResponseEntity<List<TypeProfileResponse>> list() {
        return ResponseEntity.ok(typeProfileService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTypeProfile", summary = "Consulter un type de profil")
    public ResponseEntity<TypeProfileResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(typeProfileService.findById(id));
    }

    @PostMapping
    @Operation(operationId = "createTypeProfile", summary = "Créer un type de profil")
    public ResponseEntity<TypeProfileResponse> create(@Valid @RequestBody TypeProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(typeProfileService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateTypeProfile", summary = "Mettre à jour un type de profil")
    public ResponseEntity<TypeProfileResponse> update(@PathVariable Long id, @Valid @RequestBody TypeProfileRequest request) {
        return ResponseEntity.ok(typeProfileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteTypeProfile", summary = "Supprimer un type de profil")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        typeProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
