package ci.assurzen.controller;

import ci.assurzen.dto.BalanceCompteResponse;
import ci.assurzen.dto.CompteComptableResponse;
import ci.assurzen.dto.EcritureComptableResponse;
import ci.assurzen.dto.GrandLivreLineResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.TypeOperationLedger;
import ci.assurzen.services.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${env.ws}ledger")
@Tag(name = "Ledger", description = "Grand livre comptable (partie double) - consultation")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/comptes")
    @Operation(operationId = "listComptesComptables", summary = "Lister le plan comptable")
    public ResponseEntity<List<CompteComptableResponse>> listComptes() {
        return ResponseEntity.ok(ledgerService.listComptes());
    }

    @GetMapping("/comptes/{compteId}/grand-livre")
    @Operation(operationId = "getGrandLivreCompte", summary = "Grand livre d'un compte (mouvements chronologiques avec solde progressif)")
    public ResponseEntity<List<GrandLivreLineResponse>> getGrandLivre(@PathVariable Long compteId) {
        return ResponseEntity.ok(ledgerService.getGrandLivre(compteId));
    }

    @GetMapping("/balance")
    @Operation(operationId = "getBalanceComptable", summary = "Balance generale (total debit/credit et solde de chaque compte)")
    public ResponseEntity<List<BalanceCompteResponse>> getBalance() {
        return ResponseEntity.ok(ledgerService.getBalance());
    }

    @GetMapping("/ecritures")
    @Operation(operationId = "listEcrituresComptables", summary = "Lister les ecritures comptables (paginé, filtrable par contrat et type d'operation)")
    public ResponseEntity<PageResponse<EcritureComptableResponse>> listEcritures(
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) TypeOperationLedger typeOperation,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(ledgerService.listEcritures(contractId, typeOperation, page, size));
    }

    @GetMapping("/ecritures/{id}")
    @Operation(operationId = "getEcritureComptable", summary = "Detail d'une ecriture comptable (avec ses lignes)")
    public ResponseEntity<EcritureComptableResponse> getEcriture(@PathVariable Long id) {
        return ResponseEntity.ok(ledgerService.getEcriture(id));
    }
}
