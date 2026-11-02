package ci.assurzen.controller;

import ci.assurzen.dto.CommercialPayrollEntry;
import ci.assurzen.services.CommercialPayrollService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Elements de preparation paie (§45) - reserve aux administrateurs. Ne se substitue a aucun module de
 * paie : fournit uniquement matricule/prime de base/avantages/bonus/total variable pour les mois deja
 * definitivement clotures (VALIDATED/PAID/CLOSED), a charge d'un systeme de paie externe d'en faire
 * usage.
 */
@RestController
@RequestMapping("${env.ws}commercial-payroll")
@Tag(name = "Paie commerciale", description = "Elements de remuneration des commerciaux prets pour transmission a la paie (pas un module de paie complet)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CommercialPayrollController {

    private final CommercialPayrollService service;

    @GetMapping("summary")
    @Operation(operationId = "getCommercialBenefitSummary", summary = "Agregats des primes de transport et indemnites de communication a payer / payees pour un mois comptable (reserve aux administrateurs)")
    public ResponseEntity<ci.assurzen.dto.CommercialBenefitSummary> summary(@RequestParam Long moisComptableId) {
        return ResponseEntity.ok(service.resumeAvantages(moisComptableId));
    }

    @GetMapping
    @Operation(operationId = "getCommercialPayroll", summary = "Elements de paie (matricule/salaire/avantages/bonus/total variable) des commerciaux pour un mois comptable donne, uniquement les performances definitives (reserve aux administrateurs)")
    public ResponseEntity<List<CommercialPayrollEntry>> forMois(@RequestParam Long moisComptableId) {
        return ResponseEntity.ok(service.forMois(moisComptableId));
    }
}
