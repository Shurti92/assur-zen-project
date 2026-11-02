package ci.assurzen.controller;

import ci.assurzen.dto.AvenantRequest;
import ci.assurzen.dto.AvenantResponse;
import ci.assurzen.dto.ContractAvenantSummaryResponse;
import ci.assurzen.dto.ContractFractionneSummaryResponse;
import ci.assurzen.dto.ContractRequest;
import ci.assurzen.dto.ContractResponse;
import ci.assurzen.dto.EcheanceResponse;
import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.NotificateurSmsResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PaiementPieceJointeContent;
import ci.assurzen.dto.PaiementPieceJointeResponse;
import ci.assurzen.dto.PaiementRequest;
import ci.assurzen.dto.PaiementResponse;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.services.AvenantService;
import ci.assurzen.services.ContractsService;
import ci.assurzen.services.EcheancierService;
import ci.assurzen.services.PaiementService;
import ci.assurzen.services.SmsNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("${env.ws}contracts")
@Tag(name = "Contracts", description = "Contrats (polices d'assurance)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ContractsController {

    private final ContractsService contractsService;
    private final SmsNotificationService smsNotificationService;
    private final PaiementService paiementService;
    private final EcheancierService echeancierService;
    private final AvenantService avenantService;

    @GetMapping
    @Operation(operationId = "listContracts", summary = "Lister/rechercher les contrats (paginé, filtrable par statut)")
    public ResponseEntity<PageResponse<ContractResponse>> listContracts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) CommissionStatus commissionStatus,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long insuredId,
            @RequestParam(required = false) Long salesRepId,
            @RequestParam(required = false) Long exerciceComptableId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(contractsService.search(q, status, commissionStatus, productId, insuredId, salesRepId, exerciceComptableId, page, size));
    }

    @GetMapping("/echeance-proche")
    @Operation(operationId = "listContractsEcheanceProche", summary = "Lister les contrats signés dont l'échéance est proche (par défaut < 30 jours), paginé")
    public ResponseEntity<PageResponse<ContractResponse>> echeanceProche(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(contractsService.findEcheanceProche(days, page, size));
    }

    @GetMapping("/non-renouveles")
    @Operation(operationId = "listContractsNonRenouveles", summary = "Lister les contrats signés échus et non renouvelés, paginé")
    public ResponseEntity<PageResponse<ContractResponse>> nonRenouveles(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(contractsService.findNonRenouveles(page, size));
    }

    @GetMapping("/prime-non-reversee")
    @Operation(operationId = "listContractsPrimeNonReversee", summary = "Lister, tous partenaires confondus, les contrats signés dont la prime encaissée n'a pas encore été reversée à la compagnie par le partenaire (réservé aux administrateurs)")
    public ResponseEntity<PageResponse<ContractResponse>> primeNonReverseeGlobal(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(contractsService.findPrimeNonReverseeGlobal(page, size));
    }

    @PostMapping("/{id}/prime/reverser")
    @Operation(operationId = "reverserPrimeContrat", summary = "Marquer la prime d'un contrat comme reversée par le partenaire à la compagnie (réservé aux administrateurs)")
    public ResponseEntity<ContractResponse> reverserPrime(@PathVariable Long id) {
        return ResponseEntity.ok(contractsService.reverserPrime(id));
    }

    @GetMapping("/avenants")
    @Operation(operationId = "listContractsAvecAvenant", summary = "Lister, tous partenaires confondus, les contrats ayant subi au moins un avenant (résumé agrégé par contrat, réservé aux administrateurs)")
    public ResponseEntity<PageResponse<ContractAvenantSummaryResponse>> listContractsAvecAvenant(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(avenantService.listerContratsAvecAvenant(page, size));
    }

    @PostMapping("/{id}/avenants")
    @Operation(operationId = "creerAvenantContrat", summary = "Créer un avenant sur un contrat signé (seul moyen désormais de modifier prime/commission/partenaire/compagnie une fois le contrat signé, réservé aux administrateurs)")
    public ResponseEntity<AvenantResponse> creerAvenant(@PathVariable Long id, @Valid @RequestBody AvenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avenantService.creer(id, request));
    }

    @GetMapping("/{id}/avenants")
    @Operation(operationId = "listerAvenantsContrat", summary = "Lister, numérotés chronologiquement, les avenants d'un contrat")
    public ResponseEntity<List<AvenantResponse>> listerAvenants(@PathVariable Long id) {
        return ResponseEntity.ok(avenantService.listerPourContrat(id));
    }

    @GetMapping("/fractionnes")
    @Operation(operationId = "listContractsFractionnes", summary = "Suivi des contrats en paiement fractionné (échéances payées/en attente/en retard, reste à payer)")
    public ResponseEntity<List<ContractFractionneSummaryResponse>> listFractionnes() {
        return ResponseEntity.ok(echeancierService.listFractionnes());
    }

    @GetMapping("/export")
    @Operation(operationId = "exportContracts", summary = "Exporter les contrats au format CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) CommissionStatus commissionStatus,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long insuredId,
            @RequestParam(required = false) Long salesRepId) {
        byte[] csv = contractsService.exportCsv(q, status, commissionStatus, productId, insuredId, salesRepId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("contrats.csv").build().toString())
                .body(csv);
    }

    @PostMapping("/import")
    @Operation(operationId = "importContracts", summary = "Importer des contrats en masse depuis un fichier CSV (réservé aux administrateurs)")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(contractsService.importCsv(file));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getContract", summary = "Détail d'un contrat")
    public ResponseEntity<ContractResponse> getContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractsService.getContract(id));
    }

    @PostMapping
    @Operation(operationId = "createContract", summary = "Créer un contrat")
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contractsService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateContract", summary = "Mettre à jour un contrat")
    public ResponseEntity<ContractResponse> update(@PathVariable Long id, @Valid @RequestBody ContractRequest request) {
        return ResponseEntity.ok(contractsService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteContract", summary = "Supprimer un contrat")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        contractsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    @Operation(operationId = "getContractMessages", summary = "Lister les messages SMS (programmés et envoyés) liés à un contrat")
    public ResponseEntity<List<NotificateurSmsResponse>> getContractMessages(@PathVariable Long id) {
        contractsService.getContract(id);
        return ResponseEntity.ok(smsNotificationService.getMessagesForContract(id));
    }

    @PostMapping("/{id}/paiements")
    @Operation(operationId = "enregistrerPaiementContrat", summary = "Enregistrer un paiement de prime reçu sur un contrat (déclenche l'écriture comptable)")
    public ResponseEntity<PaiementResponse> enregistrerPaiement(@PathVariable Long id, @Valid @RequestBody PaiementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paiementService.enregistrer(id, request));
    }

    @GetMapping("/{id}/paiements")
    @Operation(operationId = "listerPaiementsContrat", summary = "Lister les paiements reçus sur un contrat")
    public ResponseEntity<List<PaiementResponse>> listerPaiements(@PathVariable Long id) {
        contractsService.getContract(id);
        return ResponseEntity.ok(paiementService.listForContract(id));
    }

    @GetMapping("/{id}/paiements/{paiementId}/pieces-jointes")
    @Operation(operationId = "listPaiementPiecesJointes", summary = "Lister les pieces jointes d'un paiement")
    public ResponseEntity<List<PaiementPieceJointeResponse>> piecesJointesPaiement(@PathVariable Long id, @PathVariable Long paiementId) {
        return ResponseEntity.ok(paiementService.listPiecesJointes(id, paiementId));
    }

    @PostMapping("/{id}/paiements/{paiementId}/pieces-jointes")
    @Operation(operationId = "uploadPaiementPieceJointe", summary = "Joindre un justificatif a un paiement")
    public ResponseEntity<PaiementPieceJointeResponse> uploadPieceJointePaiement(@PathVariable Long id, @PathVariable Long paiementId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paiementService.ajouterPieceJointe(id, paiementId, file));
    }

    @GetMapping("/{id}/paiements/{paiementId}/pieces-jointes/{pieceId}")
    @Operation(operationId = "downloadPaiementPieceJointe", summary = "Telecharger/afficher un justificatif de paiement")
    public ResponseEntity<byte[]> downloadPieceJointePaiement(@PathVariable Long id, @PathVariable Long paiementId, @PathVariable Long pieceId) {
        PaiementPieceJointeContent piece = paiementService.getPieceJointeContent(id, paiementId, pieceId);
        MediaType mediaType = piece.contentType() != null
                ? MediaType.parseMediaType(piece.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(piece.fileName()).build().toString())
                .body(piece.content());
    }

    @GetMapping("/{id}/echeances")
    @Operation(operationId = "getContractEcheances", summary = "Lister l'échéancier (paiement fractionné) d'un contrat")
    public ResponseEntity<List<EcheanceResponse>> getEcheances(@PathVariable Long id) {
        contractsService.getContract(id);
        return ResponseEntity.ok(echeancierService.listForContract(id));
    }
}
