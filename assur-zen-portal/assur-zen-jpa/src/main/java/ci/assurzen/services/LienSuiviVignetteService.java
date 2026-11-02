package ci.assurzen.services;

import ci.assurzen.dto.LienSuiviVignetteResponse;
import ci.assurzen.entities.DemandeVignetteMoto;
import ci.assurzen.entities.LienSuiviVignette;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.repositories.DemandeVignetteMotoRepository;
import ci.assurzen.repositories.LienSuiviVignetteRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Lien de suivi public d'une demande de vignette (contrat "Suivi public securise par token",
 * 2026-09-17). Responsabilite unique : cycle de vie du token (generation/regeneration/revocation/
 * verification) - la composition de la reponse de suivi (historique, libelles) reste dans
 * DemandeVignetteMotoService, qui est deja proprietaire de ces donnees.
 *
 * Le token brut (256 bits, SecureRandom) n'est JAMAIS persiste - seul son hash SHA-256 l'est (cf.
 * javadoc de LienSuiviVignette). Consequence assumee : un SMS ne peut inclure le lien complet qu'au
 * moment ou le token brut existe encore en memoire, c'est-a-dire uniquement a la generation
 * (creation de la demande) et a la regeneration - jamais lors d'un simple changement de statut
 * ulterieur (DemandeVignetteMotoService renvoie alors vers "le lien deja recu", sans repeter l'URL).
 */
@Service
@RequiredArgsConstructor
public class LienSuiviVignetteService {

    private static final String CONFIG_VALIDITE_JOURS = "VIGNETTE_SUIVI_TOKEN_VALIDITE_JOURS";
    private static final long VALIDITE_JOURS_DEFAUT = 90L;
    private static final int TAILLE_TOKEN_OCTETS = 32;

    private final LienSuiviVignetteRepository repository;
    private final DemandeVignetteMotoRepository demandeVignetteMotoRepository;
    private final DynamicConfigurationService dynamicConfigurationService;
    private final AuthService authService;
    private final SmsNotificationService smsNotificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.vignette.suivi-lien-base-url}")
    private String suiviLienBaseUrl;

    @Transactional
    public LienSuiviVignetteResponse genererPourDemande(DemandeVignetteMoto demande) {
        return construireEtSauvegarder(demande);
    }

    @Transactional
    public LienSuiviVignetteResponse regenerer(Long demandeId) {
        requireAdmin();
        DemandeVignetteMoto demande = resolveDemande(demandeId);
        desactiverLienActifSiPresent(demandeId, "Remplace par un nouveau lien (regeneration)");
        LienSuiviVignetteResponse nouveauLien = construireEtSauvegarder(demande);

        smsNotificationService.sendFromTemplate(
                demande.getTelephoneAssure(),
                "VGN_MOTO_LIEN_REGENERE",
                Map.of("reference", demande.getReference(), "lienSuivi", nouveauLien.lienComplet()),
                TypeMessageSms.vignette_moto_lien_suivi_regenere);

        return nouveauLien;
    }

    @Transactional
    public void revoquer(Long demandeId, String motif) {
        requireAdmin();
        LienSuiviVignette lien = repository.findByDemandeVignetteMotoIdAndActifTrue(demandeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun lien de suivi actif pour cette demande: " + demandeId));
        lien.setActif(false);
        lien.setDateRevocation(Instant.now());
        lien.setMotifRevocation(motif);
        repository.save(lien);
    }

    /** Public - aucune authentification requise. Ne distingue JAMAIS un token inexistant d'un token
     * expire ou revoque (meme exception generique) - meme discipline "ne pas confirmer/infirmer
     * l'existence d'une ressource a un tiers" que verifierAccesDemandeCible() ailleurs dans ce
     * module, appliquee ici a un token plutot qu'a un id. */
    @Transactional
    public DemandeVignetteMoto resoudreDemandeActive(String tokenBrut) {
        String hash = hasher(tokenBrut);
        LienSuiviVignette lien = repository.findByTokenHash(hash)
                .filter(LienSuiviVignette::isActif)
                .filter(l -> l.getDateExpiration().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien de suivi introuvable ou expire"));

        lien.setDateDerniereUtilisation(Instant.now());
        repository.save(lien);
        return lien.getDemandeVignetteMoto();
    }

    // ------------------------------------------------------------------

    private void desactiverLienActifSiPresent(Long demandeId, String motif) {
        repository.findByDemandeVignetteMotoIdAndActifTrue(demandeId).ifPresent(ancien -> {
            ancien.setActif(false);
            ancien.setDateRevocation(Instant.now());
            ancien.setMotifRevocation(motif);
            repository.save(ancien);
        });
    }

    private LienSuiviVignetteResponse construireEtSauvegarder(DemandeVignetteMoto demande) {
        String tokenBrut = genererTokenBrut();
        long validiteJours = dynamicConfigurationService.getLong(CONFIG_VALIDITE_JOURS, VALIDITE_JOURS_DEFAUT);

        LienSuiviVignette lien = LienSuiviVignette.builder()
                .demandeVignetteMoto(demande)
                .tokenHash(hasher(tokenBrut))
                .dateExpiration(Instant.now().plus(validiteJours, ChronoUnit.DAYS))
                .actif(true)
                .build();
        lien = repository.save(lien);

        String lienComplet = suiviLienBaseUrl + "?token=" + tokenBrut;
        return new LienSuiviVignetteResponse(lien.getId(), demande.getId(), tokenBrut, lienComplet, lien.getDateExpiration());
    }

    private String genererTokenBrut() {
        byte[] octets = new byte[TAILLE_TOKEN_OCTETS];
        secureRandom.nextBytes(octets);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    private String hasher(String tokenBrut) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenBrut.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible sur cette JVM", e);
        }
    }

    private DemandeVignetteMoto resolveDemande(Long demandeId) {
        return demandeVignetteMotoRepository.findById(demandeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demande de vignette moto introuvable: " + demandeId));
    }

    private void requireAdmin() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        if (principal.roles() == null || !principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs");
        }
    }
}
