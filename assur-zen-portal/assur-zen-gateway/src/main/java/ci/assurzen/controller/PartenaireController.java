package ci.assurzen.controller;

import ci.assurzen.dto.ContractAvenantSummaryResponse;
import ci.assurzen.dto.ContractResponse;
import ci.assurzen.dto.HistoriqueContratResponse;
import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.ObjectifProductionRequest;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PartenaireDocumentContent;
import ci.assurzen.dto.PartenaireDocumentExpirationRequest;
import ci.assurzen.dto.PartenaireDocumentResponse;
import ci.assurzen.dto.PartenaireRequest;
import ci.assurzen.dto.PartenaireResponse;
import ci.assurzen.dto.PartialPaymentRequestCreateRequest;
import ci.assurzen.dto.PartialPaymentRequestResponse;
import ci.assurzen.dto.PartialPaymentStatusResponse;
import ci.assurzen.dto.PrimeObjectifSimulationResponse;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.services.AvenantService;
import ci.assurzen.services.CommissionPartialPaymentService;
import ci.assurzen.services.ContractsService;
import ci.assurzen.services.PartenaireService;
import ci.assurzen.services.PrimeObjectifService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("${env.ws}partenaires")
@Tag(name = "Partenaires", description = "CRUD des partenaires")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PartenaireController {

    private final PartenaireService service;
    private final ContractsService contractsService;
    private final CommissionPartialPaymentService commissionPartialPaymentService;
    private final AvenantService avenantService;
    private final PrimeObjectifService primeObjectifService;

    @GetMapping
    @Operation(operationId = "listPartenaires", summary = "Lister/rechercher les partenaires (paginé)")
    public ResponseEntity<PageResponse<PartenaireResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) PartenaireType type,
            @RequestParam(required = false) Boolean documentsEnAttente,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.search(q, active, type, documentsEnAttente, page, size));
    }

    @GetMapping("/export")
    @Operation(operationId = "exportPartenaires", summary = "Exporter les partenaires au format CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) PartenaireType type) {
        byte[] csv = service.exportCsv(q, active, type);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("partenaires.csv").build().toString())
                .body(csv);
    }

    @PostMapping("/import")
    @Operation(operationId = "importPartenaires", summary = "Importer des partenaires en masse depuis un fichier CSV")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.importCsv(file));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPartenaire", summary = "Consulter un partenaire")
    public ResponseEntity<PartenaireResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/{id}/contracts")
    @Operation(operationId = "getPartenaireContracts",
            summary = "Lister les contrats d'un partenaire (paginé) - masque par défaut les contrats signés, échus, non renouvelés depuis plus de 2 mois (config CONTRACT_NON_RENOUVELE_MASQUAGE_MOIS) ; inclureAnciensNonRenouveles=true pour les revoir")
    public ResponseEntity<PageResponse<ContractResponse>> contracts(
            @PathVariable Long id,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) Boolean inclureAnciensNonRenouveles,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(contractsService.findByPartenaire(id, search, status, inclureAnciensNonRenouveles, page, size));
    }

    @GetMapping("/{id}/contracts/avenants")
    @Operation(operationId = "getPartenaireContractsAvecAvenant",
            summary = "Lister les contrats de ce partenaire ayant subi au moins un avenant (résumé agrégé par contrat)")
    public ResponseEntity<PageResponse<ContractAvenantSummaryResponse>> contractsAvecAvenant(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(avenantService.listerContratsAvecAvenantParPartenaire(id, page, size));
    }

    @GetMapping("/{id}/contracts/historique")
    @Operation(operationId = "getPartenaireContractsHistorique",
            summary = "Historique complet (avenants + audit) de tous les contrats de ce partenaire, en une seule timeline chronologique paginée")
    public ResponseEntity<PageResponse<HistoriqueContratResponse>> contractsHistorique(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(avenantService.historiquePartenaire(id, page, size));
    }

    @GetMapping("/{id}/contracts/prime-non-reversee")
    @Operation(operationId = "getPartenaireContractsPrimeNonReversee",
            summary = "Lister les contrats signés de ce partenaire dont la prime encaissée n'a pas encore été reversée à la compagnie (paginé)")
    public ResponseEntity<PageResponse<ContractResponse>> contractsPrimeNonReversee(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(contractsService.findPrimeNonReverseeByPartenaire(id, page, size));
    }

    @PostMapping("/{id}/commissions/partial-payment-requests")
    @Operation(operationId = "requestPartialCommissionPayment",
            summary = "Demander le versement anticipe d'une partie du solde de commissions en attente (montant saisi par le partenaire, plafonne a 40% du solde et revalide cote serveur, 2x/jour max). "
                    + "Header optionnel 'Idempotency-Key' (UUID v4, recommande) : un rejeu de la meme cle pour ce partenaire renvoie la demande deja creee au lieu d'en creer une seconde.")
    public ResponseEntity<PartialPaymentRequestResponse> requestPartialPayment(
            @PathVariable Long id,
            @Valid @RequestBody PartialPaymentRequestCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commissionPartialPaymentService.demanderPaiementPartiel(id, request.amount(), idempotencyKey));
    }

    @GetMapping("/{id}/commissions/partial-payment-requests")
    @Operation(operationId = "getPartialCommissionPaymentStatus",
            summary = "Solde en attente, quota journalier restant et historique des demandes de paiement partiel")
    public ResponseEntity<PartialPaymentStatusResponse> partialPaymentStatus(@PathVariable Long id) {
        return ResponseEntity.ok(commissionPartialPaymentService.getStatus(id));
    }

    @GetMapping("/{id}/prime-objectif/simulation")
    @Operation(operationId = "simulerPrimeObjectif", summary = "Apercu sans engagement de la prime d'objectif de ce partenaire (ne persiste rien, reserve aux administrateurs)")
    public ResponseEntity<PrimeObjectifSimulationResponse> simulerPrimeObjectif(@PathVariable Long id) {
        return ResponseEntity.ok(primeObjectifService.simuler(id));
    }

    @GetMapping("/{id}/documents")
    @Operation(operationId = "listPartenaireDocuments", summary = "Lister les documents joints a un partenaire (contrat apporteur, CNI, RIB...)")
    public ResponseEntity<List<PartenaireDocumentResponse>> documents(@PathVariable Long id) {
        return ResponseEntity.ok(service.listDocuments(id));
    }

    @PostMapping("/{id}/documents")
    @Operation(operationId = "uploadPartenaireDocument",
            summary = "Joindre un document a un partenaire (multipart: file + type, dateExpiration AAAA-MM-JJ facultative)")
    public ResponseEntity<PartenaireDocumentResponse> uploadDocument(@PathVariable Long id,
                                                                       @RequestParam("file") MultipartFile file,
                                                                       @RequestParam("type") String type,
                                                                       @RequestParam(value = "dateExpiration", required = false)
                                                                       @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                                                       java.time.LocalDate dateExpiration) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterDocument(id, type, file, dateExpiration));
    }

    @PatchMapping("/{id}/documents/{documentId}/validation")
    @Operation(operationId = "validatePartenaireDocument",
            summary = "Valider ou refuser (motif obligatoire) un document joint a un partenaire (reserve aux administrateurs)")
    public ResponseEntity<PartenaireDocumentResponse> validateDocument(@PathVariable Long id, @PathVariable Long documentId,
                                                                         @RequestBody ci.assurzen.dto.PartenaireDocumentValidationRequest request) {
        return ResponseEntity.ok(service.validerDocument(id, documentId, request.statut(), request.motif()));
    }

    @PatchMapping("/{id}/documents/{documentId}")
    @Operation(operationId = "updatePartenaireDocumentExpiration",
            summary = "Fixer ou effacer (null) la date d'expiration d'un document joint a un partenaire")
    public ResponseEntity<PartenaireDocumentResponse> updateDocumentExpiration(@PathVariable Long id, @PathVariable Long documentId,
                                                                                 @RequestBody PartenaireDocumentExpirationRequest request) {
        return ResponseEntity.ok(service.definirDateExpiration(id, documentId, request.dateExpiration()));
    }

    @GetMapping("/{id}/documents/{documentId}")
    @Operation(operationId = "downloadPartenaireDocument", summary = "Telecharger/afficher un document joint a un partenaire")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, @PathVariable Long documentId) {
        PartenaireDocumentContent document = service.getDocumentContent(id, documentId);
        // PDF/JPEG/PNG controles a l'envoi (type deduit de la signature) : affichables en ligne. Tout autre
        // type (documents anterieurs au controle du 2026-10-05) : piece jointe binaire, jamais interpretee
        // par le navigateur.
        boolean admis = ci.assurzen.utils.DocumentFichierValidator.estFormatAdmis(document.contentType());
        return ResponseEntity.ok()
                .contentType(admis ? MediaType.parseMediaType(document.contentType()) : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (admis ? ContentDisposition.inline() : ContentDisposition.attachment())
                                .filename(document.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.content());
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @Operation(operationId = "deletePartenaireDocument", summary = "Supprimer un document joint a un partenaire")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        service.deleteDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @Operation(operationId = "createPartenaire", summary = "Creer un partenaire")
    public ResponseEntity<PartenaireResponse> create(@Valid @RequestBody PartenaireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updatePartenaire", summary = "Mettre a jour un partenaire")
    public ResponseEntity<PartenaireResponse> update(@PathVariable Long id, @Valid @RequestBody PartenaireRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/objectif")
    @Operation(operationId = "definirObjectifProductionPartenaire", summary = "Definir/mettre a jour uniquement l'objectif de production d'un partenaire, sans repasser par le PUT complet")
    public ResponseEntity<PartenaireResponse> definirObjectifProduction(@PathVariable Long id, @Valid @RequestBody ObjectifProductionRequest request) {
        return ResponseEntity.ok(service.definirObjectifProduction(id, request.objectifProduction()));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deletePartenaire", summary = "Supprimer un partenaire")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
