package ci.assurzen.controller;

import ci.assurzen.dto.CreateLoanRepaymentRequest;
import ci.assurzen.dto.EarlyRepaymentRequest;
import ci.assurzen.dto.LoanBalanceResponse;
import ci.assurzen.dto.LoanInstallmentResponse;
import ci.assurzen.dto.LoanRepaymentResponse;
import ci.assurzen.dto.LoanResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.LoanRepaymentService;
import ci.assurzen.services.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("${env.ws}loans")
@Tag(name = "Prets partenaires", description = "Echeancier, remboursements, solde, retards (extension du moteur Sorties de fonds)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final LoanRepaymentService loanRepaymentService;

    @GetMapping("/search")
    @Operation(operationId = "searchLoans", summary = "Rechercher des prets (pagine, filtrable)")
    public ResponseEntity<PageResponse<LoanResponse>> search(
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long amountMin,
            @RequestParam(required = false) Long amountMax,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(loanService.search(partnerId, reference, dateFrom, dateTo, amountMin, amountMax, overdue, page, size));
    }

    @GetMapping("/overdue")
    @Operation(operationId = "listOverdueLoans", summary = "Lister les prets ayant au moins une echeance en retard")
    public ResponseEntity<PageResponse<LoanResponse>> overdue(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(loanService.listOverdue(page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getLoan", summary = "Detail d'un pret")
    public ResponseEntity<LoanResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.get(id));
    }

    @GetMapping("/{id}/installments")
    @Operation(operationId = "listLoanInstallments", summary = "Echeancier complet d'un pret")
    public ResponseEntity<List<LoanInstallmentResponse>> installments(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.listInstallments(id));
    }

    @GetMapping("/{id}/repayments")
    @Operation(operationId = "listLoanRepayments", summary = "Remboursements enregistres sur un pret")
    public ResponseEntity<List<LoanRepaymentResponse>> repayments(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.listRepayments(id));
    }

    @PostMapping("/{id}/repayments")
    @Operation(operationId = "createLoanRepayment", summary = "Enregistrer un remboursement (reserve aux administrateurs). "
            + "Alloue automatiquement le montant sur les echeances impayees les plus anciennes (FIFO), une ou plusieurs, partiellement ou totalement.")
    public ResponseEntity<LoanRepaymentResponse> repay(@PathVariable Long id, @Valid @RequestBody CreateLoanRepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanRepaymentService.registerRepayment(id, request));
    }

    @GetMapping("/{id}/balance")
    @Operation(operationId = "getLoanBalance", summary = "Solde restant d'un pret (toujours recalcule cote serveur)")
    public ResponseEntity<LoanBalanceResponse> balance(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.balance(id));
    }

    @PostMapping("/{id}/early-repayment")
    @Operation(operationId = "earlyRepaymentLoan", summary = "Solder un pret avant sa date prevue (reserve aux administrateurs)")
    public ResponseEntity<LoanRepaymentResponse> earlyRepayment(@PathVariable Long id, @Valid @RequestBody EarlyRepaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.earlyRepayment(id, request));
    }
}
