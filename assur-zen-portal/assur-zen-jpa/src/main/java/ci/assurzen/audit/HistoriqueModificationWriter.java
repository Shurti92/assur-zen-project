package ci.assurzen.audit;

import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CanalAudit;
import ci.assurzen.entities.HistoriqueModification;
import ci.assurzen.entities.StatutAudit;
import ci.assurzen.repositories.HistoriqueModificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Separee de FunctionalAuditRecorder car @Async ne fonctionne que sur un appel inter-bean (comme
 * CampagneEnvoiService/CampagneMarketingService) : l'auto-invocation depuis la meme classe
 * contournerait le proxy Spring et ecrirait en synchrone, couplant l'ecriture d'audit a la
 * transaction de l'appelant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoriqueModificationWriter {

    private final HistoriqueModificationRepository historiqueModificationRepository;

    @Async("auditTaskExecutor")
    public void persistAsync(String reference, Long userId, String username, String userRole, String correlationId,
                              String ipAddress, CanalAudit canal, String module, String entityName, String entityId,
                              ActionAudit action, String description, String ancienneValeur, String nouvelleValeur,
                              String champsModifies, String motif, StatutAudit statut, String errorMessage) {
        try {
            HistoriqueModification entree = HistoriqueModification.builder()
                    .reference(reference)
                    .correlationId(correlationId)
                    .module(module)
                    .entityName(entityName)
                    .entityId(entityId)
                    .action(action)
                    .description(description)
                    .motif(motif)
                    .ancienneValeur(ancienneValeur)
                    .nouvelleValeur(nouvelleValeur)
                    .champsModifies(champsModifies)
                    .userId(userId)
                    .username(username == null ? "system" : username)
                    .userRole(userRole)
                    .ipAddress(ipAddress)
                    .canal(canal)
                    .statut(statut)
                    .errorMessage(errorMessage)
                    .build();
            historiqueModificationRepository.save(entree);
        } catch (Exception e) {
            log.error("Echec ecriture historique modification module={} entite={}/{} action={}: {}",
                    module, entityName, entityId, action, e.getMessage(), e);
        }
    }
}
