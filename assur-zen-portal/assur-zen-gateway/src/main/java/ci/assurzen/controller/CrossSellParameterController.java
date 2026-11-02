package ci.assurzen.controller;

import ci.assurzen.dto.CrossSellParameterRequest;
import ci.assurzen.dto.CrossSellParameterResponse;
import ci.assurzen.services.CrossSellParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}cross-sell/parametres")
@Tag(name = "Vente Croisee - Parametres", description = "Parametres cle/valeur du moteur (score minimum, expiration...)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CrossSellParameterController {

    private final CrossSellParameterService crossSellParameterService;

    @GetMapping
    @Operation(operationId = "listCrossSellParameters", summary = "Lister les parametres")
    public ResponseEntity<List<CrossSellParameterResponse>> list() {
        return ResponseEntity.ok(crossSellParameterService.list());
    }

    @PutMapping
    @Operation(operationId = "upsertCrossSellParameter", summary = "Creer/mettre a jour un parametre (reserve aux administrateurs/managers)")
    public ResponseEntity<CrossSellParameterResponse> upsert(@Valid @RequestBody CrossSellParameterRequest request) {
        return ResponseEntity.ok(crossSellParameterService.upsert(request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteCrossSellParameter", summary = "Supprimer un parametre (reserve aux administrateurs/managers)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        crossSellParameterService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
