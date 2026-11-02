package ci.assurzen.controller;

import ci.assurzen.dto.AvantageRequest;
import ci.assurzen.dto.AvantageResponse;
import ci.assurzen.services.AvantageService;
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
@RequestMapping("${env.ws}fidelite/avantages")
@Tag(name = "Fidelite - Avantages VIP", description = "Catalogue des avantages (gestionnaire dedie, traitement prioritaire...), distincts des recompenses a points")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AvantageController {

    private final AvantageService avantageService;

    @GetMapping
    @Operation(operationId = "listAvantagesCatalogue", summary = "Lister le catalogue d'avantages VIP")
    public ResponseEntity<List<AvantageResponse>> list() {
        return ResponseEntity.ok(avantageService.list());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAvantageCatalogue", summary = "Detail d'un avantage")
    public ResponseEntity<AvantageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(avantageService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createAvantageCatalogue", summary = "Ajouter un avantage au catalogue (reserve aux administrateurs/managers)")
    public ResponseEntity<AvantageResponse> create(@Valid @RequestBody AvantageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avantageService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateAvantageCatalogue", summary = "Mettre a jour un avantage (reserve aux administrateurs/managers)")
    public ResponseEntity<AvantageResponse> update(@PathVariable Long id, @Valid @RequestBody AvantageRequest request) {
        return ResponseEntity.ok(avantageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteAvantageCatalogue", summary = "Retirer un avantage du catalogue (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        avantageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
