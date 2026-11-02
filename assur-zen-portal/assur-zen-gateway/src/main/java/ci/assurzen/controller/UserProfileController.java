package ci.assurzen.controller;

import ci.assurzen.dto.ImportResult;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.PurgeSessionsResult;
import ci.assurzen.dto.ResetPasswordRequest;
import ci.assurzen.dto.UserProfileRequest;
import ci.assurzen.dto.UserProfileResponse;
import ci.assurzen.dto.UserProfileUpdateRequest;
import ci.assurzen.dto.UserSessionResponse;
import ci.assurzen.dto.UserStatusRequest;
import ci.assurzen.services.UserProfileService;
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
@RequestMapping("${env.ws}users")
@Tag(name = "Users", description = "Gestion des comptes utilisateurs et de leurs profils")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    @Operation(operationId = "listUserProfiles", summary = "Lister/rechercher les utilisateurs et leurs profils (paginé)")
    public ResponseEntity<PageResponse<UserProfileResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String roles,
            @RequestParam(required = false) Long typeProfileId,
            @RequestParam(required = false) Long villePartenaireId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(userProfileService.search(q, active, roles, typeProfileId, villePartenaireId, page, size));
    }

    @GetMapping("/roles")
    @Operation(operationId = "listAvailableRoles", summary = "Lister les rôles disponibles")
    public ResponseEntity<List<String>> listRoles() {
        return ResponseEntity.ok(userProfileService.getAvailableRoles());
    }

    @GetMapping("/export")
    @Operation(operationId = "exportUserProfiles", summary = "Exporter la liste des utilisateurs et leurs profils au format CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String roles,
            @RequestParam(required = false) Long typeProfileId,
            @RequestParam(required = false) Long villePartenaireId) {
        byte[] csv = userProfileService.exportCsv(q, active, roles, typeProfileId, villePartenaireId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("utilisateurs.csv").build().toString())
                .body(csv);
    }

    @PostMapping("/import")
    @Operation(operationId = "importUserProfiles", summary = "Importer des utilisateurs en masse depuis un fichier CSV")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userProfileService.importCsv(file));
    }

    @PostMapping
    @Operation(operationId = "createUserProfile", summary = "Créer un utilisateur et son profil en une seule opération")
    public ResponseEntity<UserProfileResponse> create(@Valid @RequestBody UserProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfileService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateUserProfile", summary = "Modifier un utilisateur et son profil")
    public ResponseEntity<UserProfileResponse> update(@PathVariable Long id, @Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(userProfileService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteUserProfile", summary = "Supprimer un utilisateur et son profil")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reset-password")
    @Operation(operationId = "resetUserPassword", summary = "Réinitialiser le mot de passe d'un utilisateur")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userProfileService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @Operation(operationId = "setUserStatus", summary = "Activer ou désactiver un utilisateur")
    public ResponseEntity<UserProfileResponse> setActive(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return ResponseEntity.ok(userProfileService.setActive(id, request));
    }

    /**
     * Distinct de resetPassword() ci-dessus : ici le mot de passe est genere par le backend (jamais
     * choisi par l'admin) et envoye par SMS au titulaire, jamais renvoye dans la reponse HTTP -
     * contrat "Renvoi automatique des acces (SMS) a un partenaire deja cree" (2026-08-25).
     */
    @PostMapping("/{id}/resend-access")
    @Operation(operationId = "resendUserAccess", summary = "Regenerer un mot de passe temporaire et le renvoyer par SMS au titulaire du compte (le mot de passe n'apparait jamais dans la reponse)")
    public ResponseEntity<UserProfileResponse> resendAccess(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.resendAccess(id));
    }

    @GetMapping("/{id}/login-history")
    @Operation(operationId = "getUserLoginHistory", summary = "Historique des connexions d'un utilisateur")
    public ResponseEntity<PageResponse<UserSessionResponse>> loginHistory(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ResponseEntity.ok(userProfileService.getLoginHistory(id, page, size));
    }

    @DeleteMapping("/sessions/revoked")
    @Operation(operationId = "purgeRevokedSessions", summary = "Supprimer définitivement les sessions révoquées")
    public ResponseEntity<PurgeSessionsResult> purgeRevokedSessions() {
        return ResponseEntity.ok(userProfileService.purgeRevokedSessions());
    }
}
