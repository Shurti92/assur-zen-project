package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.ReclamationAffectationRequest;
import ci.assurzen.dto.ReclamationClotureRequest;
import ci.assurzen.dto.ReclamationCommentaireRequest;
import ci.assurzen.dto.ReclamationCommentaireResponse;
import ci.assurzen.dto.ReclamationPieceJointeContent;
import ci.assurzen.dto.ReclamationPieceJointeResponse;
import ci.assurzen.dto.ReclamationRequest;
import ci.assurzen.dto.ReclamationResponse;
import ci.assurzen.dto.ReclamationStatistiquesResponse;
import ci.assurzen.dto.ReclamationStatutRequest;
import ci.assurzen.entities.ReclamationCanal;
import ci.assurzen.entities.ReclamationPriorite;
import ci.assurzen.entities.ReclamationStatut;
import ci.assurzen.entities.ReclamationType;
import ci.assurzen.services.ReclamationService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("${env.ws}reclamations")
@Tag(name = "Reclamations", description = "Cycle de vie des reclamations clients (creation, affectation, traitement, cloture)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ReclamationsController {

    private final ReclamationService reclamationService;

    @GetMapping
    @Operation(operationId = "listReclamations", summary = "Lister/rechercher les reclamations (paginé, filtrable)")
    public ResponseEntity<PageResponse<ReclamationResponse>> list(
            @RequestParam(required = false) ReclamationStatut statut,
            @RequestParam(required = false) ReclamationType type,
            @RequestParam(required = false) ReclamationPriorite priorite,
            @RequestParam(required = false) ReclamationCanal canal,
            @RequestParam(required = false) Long gestionnaireId,
            @RequestParam(required = false) Long insuredId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(reclamationService.search(statut, type, priorite, canal, gestionnaireId, insuredId, page, size));
    }

    @GetMapping("/statistiques")
    @Operation(operationId = "getReclamationsStatistiques", summary = "Statistiques agregees sur les reclamations visibles par l'utilisateur")
    public ResponseEntity<ReclamationStatistiquesResponse> statistiques() {
        return ResponseEntity.ok(reclamationService.statistiques());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getReclamation", summary = "Detail d'une reclamation")
    public ResponseEntity<ReclamationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(reclamationService.get(id));
    }

    @PostMapping
    @Operation(operationId = "createReclamation", summary = "Creer une reclamation")
    public ResponseEntity<ReclamationResponse> create(@Valid @RequestBody ReclamationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reclamationService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateReclamation", summary = "Mettre a jour les informations descriptives d'une reclamation (le statut se change via /statut)")
    public ResponseEntity<ReclamationResponse> update(@PathVariable Long id, @Valid @RequestBody ReclamationRequest request) {
        return ResponseEntity.ok(reclamationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteReclamation",
            summary = "Supprimer une reclamation - refuse (409) des qu'elle a un commentaire, une piece jointe, ou a ete creee automatiquement (score NPS bas) : reserve a la purge d'un enregistrement de test/doublon sans historique, pas a un dossier deja engage")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reclamationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/affectation")
    @Operation(operationId = "affecterReclamation", summary = "Affecter la reclamation a un gestionnaire/service")
    public ResponseEntity<ReclamationResponse> affecter(@PathVariable Long id, @Valid @RequestBody ReclamationAffectationRequest request) {
        return ResponseEntity.ok(reclamationService.affecter(id, request));
    }

    @PatchMapping("/{id}/statut")
    @Operation(operationId = "changerStatutReclamation", summary = "Faire avancer le statut de la reclamation")
    public ResponseEntity<ReclamationResponse> changerStatut(@PathVariable Long id, @Valid @RequestBody ReclamationStatutRequest request) {
        return ResponseEntity.ok(reclamationService.changerStatut(id, request));
    }

    @PostMapping("/{id}/cloture")
    @Operation(operationId = "cloturerReclamation", summary = "Cloturer une reclamation resolue")
    public ResponseEntity<ReclamationResponse> cloturer(@PathVariable Long id, @Valid @RequestBody ReclamationClotureRequest request) {
        return ResponseEntity.ok(reclamationService.cloturer(id, request));
    }

    @GetMapping("/{id}/commentaires")
    @Operation(operationId = "listReclamationCommentaires", summary = "Lister les actions/commentaires d'une reclamation")
    public ResponseEntity<List<ReclamationCommentaireResponse>> commentaires(@PathVariable Long id) {
        return ResponseEntity.ok(reclamationService.listCommentaires(id));
    }

    @PostMapping("/{id}/commentaires")
    @Operation(operationId = "addReclamationCommentaire", summary = "Ajouter une action/commentaire de traitement")
    public ResponseEntity<ReclamationCommentaireResponse> ajouterCommentaire(@PathVariable Long id, @Valid @RequestBody ReclamationCommentaireRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reclamationService.ajouterCommentaire(id, request));
    }

    @GetMapping("/{id}/pieces-jointes")
    @Operation(operationId = "listReclamationPiecesJointes", summary = "Lister les pieces jointes d'une reclamation")
    public ResponseEntity<List<ReclamationPieceJointeResponse>> piecesJointes(@PathVariable Long id) {
        return ResponseEntity.ok(reclamationService.listPiecesJointes(id));
    }

    @PostMapping("/{id}/pieces-jointes")
    @Operation(operationId = "uploadReclamationPieceJointe", summary = "Joindre un fichier a une reclamation")
    public ResponseEntity<ReclamationPieceJointeResponse> uploadPieceJointe(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reclamationService.ajouterPieceJointe(id, file));
    }

    @GetMapping("/{id}/pieces-jointes/{pieceId}")
    @Operation(operationId = "downloadReclamationPieceJointe", summary = "Telecharger/afficher une piece jointe")
    public ResponseEntity<byte[]> downloadPieceJointe(@PathVariable Long id, @PathVariable Long pieceId) {
        ReclamationPieceJointeContent piece = reclamationService.getPieceJointeContent(id, pieceId);
        MediaType mediaType = piece.contentType() != null
                ? MediaType.parseMediaType(piece.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(piece.fileName()).build().toString())
                .body(piece.content());
    }
}
