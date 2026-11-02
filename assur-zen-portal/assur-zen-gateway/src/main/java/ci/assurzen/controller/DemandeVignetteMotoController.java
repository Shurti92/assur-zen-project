package ci.assurzen.controller;

import ci.assurzen.dto.ChangerStatutDemandeVignetteMotoRequest;
import ci.assurzen.dto.DemandeVignetteMotoRequest;
import ci.assurzen.dto.DemandeVignetteMotoResponse;
import ci.assurzen.dto.GedDocumentResponse;
import ci.assurzen.dto.GedDocumentVersionContent;
import ci.assurzen.dto.HistoriqueDemandeVignetteMotoResponse;
import ci.assurzen.dto.LienSuiviVignetteResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RejeterDemandeVignetteMotoRequest;
import ci.assurzen.dto.RevoquerLienSuiviRequest;
import ci.assurzen.dto.SuiviPublicVignetteResponse;
import ci.assurzen.entities.StatutDemandeVignetteMoto;
import ci.assurzen.services.DemandeVignetteMotoService;
import ci.assurzen.services.LienSuiviVignetteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("${env.ws}vignettes-moto/demandes")
@Tag(name = "Vignettes Moto - Demandes", description = "Creation et suivi des demandes d'etablissement de vignette moto")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DemandeVignetteMotoController {

    private final DemandeVignetteMotoService service;
    private final LienSuiviVignetteService lienSuiviVignetteService;

    @PostMapping
    @Operation(operationId = "creerDemandeVignetteMoto", summary = "Creer une demande de vignette moto (tarif fige immediatement, statut initial BROUILLON)")
    public ResponseEntity<DemandeVignetteMotoResponse> creer(@Valid @RequestBody DemandeVignetteMotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(request));
    }

    @GetMapping
    @Operation(operationId = "listerDemandesVignetteMoto", summary = "Lister/filtrer les demandes de vignette moto (paginee)")
    public ResponseEntity<PageResponse<DemandeVignetteMotoResponse>> list(
            @RequestParam(required = false) StatutDemandeVignetteMoto statut,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.list(statut, page, size));
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getDemandeVignetteMoto", summary = "Consulter une demande de vignette moto")
    public ResponseEntity<DemandeVignetteMotoResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @Operation(operationId = "uploadDemandeVignetteMotoDocument",
            summary = "Joindre un document a une demande (types : CNI_ASSURE, RECU_ACHAT_MOTO, PHOTO_MOTO, PHOTO_CHASSIS, ASSURANCE, ANCIENNE_VIGNETTE) - reutilise le module GED")
    public ResponseEntity<GedDocumentResponse> ajouterDocument(
            @PathVariable Long id,
            @Parameter(description = "Code GedDocumentType : CNI_ASSURE, RECU_ACHAT_MOTO, PHOTO_MOTO, PHOTO_CHASSIS, ASSURANCE ou ANCIENNE_VIGNETTE")
            @RequestParam String typeCode,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ajouterDocument(id, typeCode, file));
    }

    @GetMapping("/{id}/documents")
    @Operation(operationId = "listerDemandeVignetteMotoDocuments", summary = "Lister les documents joints a une demande")
    public ResponseEntity<PageResponse<GedDocumentResponse>> listerDocuments(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(service.listerDocuments(id, page, size));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @Operation(operationId = "downloadDemandeVignetteMotoDocument",
            summary = "Telecharger un document joint a une demande (reserve au createur de la demande ou a un administrateur - "
                    + "endpoint scope, distinct de GET /ged/documents/{id}/download qui reste reserve aux administrateurs)")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id, @PathVariable Long documentId) {
        GedDocumentVersionContent piece = service.downloadDocument(id, documentId);
        MediaType mediaType = piece.contentType() != null
                ? MediaType.parseMediaType(piece.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(piece.fileName()).build().toString())
                .body(piece.content());
    }

    @PostMapping("/{id}/soumettre")
    @Operation(operationId = "soumettreDemandeVignetteMoto",
            summary = "Soumettre une demande (BROUILLON -> EN_ATTENTE_PAIEMENT), refuse en 409 si un document obligatoire manque")
    public ResponseEntity<DemandeVignetteMotoResponse> soumettre(@PathVariable Long id) {
        return ResponseEntity.ok(service.soumettre(id));
    }

    @PatchMapping("/{id}/annuler")
    @Operation(operationId = "annulerDemandeVignetteMoto", summary = "Annuler une demande (tant qu'elle n'est pas livree)")
    public ResponseEntity<DemandeVignetteMotoResponse> annuler(@PathVariable Long id) {
        return ResponseEntity.ok(service.annuler(id));
    }

    @PatchMapping("/{id}/rejeter")
    @Operation(operationId = "rejeterDemandeVignetteMoto", summary = "Rejeter une demande avec motif (depuis n'importe quel statut non terminal)")
    public ResponseEntity<DemandeVignetteMotoResponse> rejeter(@PathVariable Long id, @Valid @RequestBody RejeterDemandeVignetteMotoRequest request) {
        return ResponseEntity.ok(service.rejeter(id, request.motif()));
    }

    @PostMapping("/{id}/statut")
    @Operation(operationId = "changerStatutDemandeVignetteMoto",
            summary = "Faire progresser manuellement une demande (PAYEE, EN_TRAITEMENT, EN_EDITION, EDITEE, EN_LIVRAISON, LIVREE ou REJETEE) - reserve a l'admin/gestionnaire")
    public ResponseEntity<DemandeVignetteMotoResponse> changerStatut(@PathVariable Long id, @Valid @RequestBody ChangerStatutDemandeVignetteMotoRequest request) {
        return ResponseEntity.ok(service.changerStatut(id, request.statut(), request.motif(), request.referenceOperation()));
    }

    @GetMapping("/{id}/historique")
    @Operation(operationId = "getHistoriqueDemandeVignetteMoto", summary = "Consulter l'historique complet des changements de statut d'une demande")
    public ResponseEntity<List<HistoriqueDemandeVignetteMotoResponse>> getHistorique(@PathVariable Long id) {
        return ResponseEntity.ok(service.getHistorique(id));
    }

    @PostMapping("/{id}/lien-suivi/regeneration")
    @Operation(operationId = "regenererLienSuiviVignette",
            summary = "Regenerer le lien de suivi public d'une demande (invalide l'ancien, renvoie le nouveau par SMS) - reserve a l'admin")
    public ResponseEntity<LienSuiviVignetteResponse> regenererLienSuivi(@PathVariable Long id) {
        return ResponseEntity.ok(lienSuiviVignetteService.regenerer(id));
    }

    @PostMapping("/{id}/lien-suivi/revocation")
    @Operation(operationId = "revoquerLienSuiviVignette", summary = "Revoquer le lien de suivi public actif d'une demande - reserve a l'admin")
    public ResponseEntity<Void> revoquerLienSuivi(@PathVariable Long id, @Valid @RequestBody RevoquerLienSuiviRequest request) {
        lienSuiviVignetteService.revoquer(id, request.motif());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suivi-public")
    @Operation(operationId = "getSuiviPublicVignette",
            summary = "Consulter publiquement (sans authentification) le suivi d'une demande via son token de lien de suivi")
    @SecurityRequirement(name = "")
    public ResponseEntity<SuiviPublicVignetteResponse> getSuiviPublic(@RequestParam String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getSuiviPublic(token));
    }
}
