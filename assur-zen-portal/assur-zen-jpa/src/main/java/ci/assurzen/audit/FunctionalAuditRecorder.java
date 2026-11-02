package ci.assurzen.audit;

import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.CanalAudit;
import ci.assurzen.entities.StatutAudit;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.services.CodeGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Point d'entree unique pour ecrire dans HistoriqueModification (audit fonctionnel). Utilise par
 * AuditAspect (actions sans diff : CREATE/DELETE/ACTIVATE/APPROVE/...) et directement par les
 * services pour les UPDATE avec diff avant/apres.
 *
 * Le contexte (principal, correlationId MDC, IP MDC) est TOUJOURS resolu ici, sur le thread
 * appelant, AVANT de deleguer l'ecriture a HistoriqueModificationWriter (@Async) : MDC est un
 * ThreadLocal qui ne traverse pas automatiquement les threads du pool audit-flux-*.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FunctionalAuditRecorder {

    private final HistoriqueModificationWriter historiqueModificationWriter;
    private final CodeGeneratorService codeGeneratorService;
    // Injection par champ (pas via le constructeur @RequiredArgsConstructor) : AuthService se
    // retrouve, via des services qui ecrivent maintenant dans l'audit trail (ex. OtpService ->
    // ... -> ProviderService -> DynamicConfigurationService -> ce service), dans le propre arbre de
    // dependances d'AuthService - une injection constructeur cree un cycle de beans. @Lazy sur un
    // champ genere par Lombok n'est PAS recopie sur le parametre du constructeur (confirme au
    // demarrage - le cycle persistait) : l'injection par champ @Autowired @Lazy est la seule forme
    // fiable ici. authService n'est utilise que pour resoudre le principal courant (resolveContexte,
    // hors chemin chaud), donc differer sa resolution via proxy n'a aucun impact fonctionnel.
    @Autowired
    @Lazy
    private AuthService authService;
    // Construit localement (pas de bean Spring injectable ici) : ce projet a a la fois Jackson 2
    // (com.fasterxml, utilise partout dans le code metier) et Jackson 3 (tools.jackson, cible par
    // JacksonAutoConfiguration de Spring Boot 4) sur le classpath - aucun bean ObjectMapper
    // com.fasterxml.jackson.databind n'est publie par le framework. JavaTimeModule est requis :
    // un "new ObjectMapper()" nu echoue silencieusement (cf toJson) des qu'un diff contient un
    // Instant/LocalDate (ex. DynamicConfigurationResponse.updatedAt) - bug reel trouve sur restore().
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void recordAction(String module, String entityName, String entityId, ActionAudit action, String description) {
        Contexte contexte = resolveContexte();
        dispatch(contexte, module, entityName, entityId, action, description, null, null, null, null, StatutAudit.SUCCES, null);
    }

    public void recordActionFailure(String module, String entityName, String entityId, ActionAudit action, String errorMessage) {
        Contexte contexte = resolveContexte();
        dispatch(contexte, module, entityName, entityId, action, null, null, null, null, null, StatutAudit.ECHEC, errorMessage);
    }

    /**
     * Diff avant/apres par reflection sur les composants d'un record (les DTO XxxResponse de ce
     * projet sont tous des records). N'enregistre rien si aucun champ n'a reellement change.
     */
    public void recordUpdate(String module, String entityName, String entityId, Object avant, Object apres, String motif) {
        if (avant == null || apres == null || !avant.getClass().equals(apres.getClass()) || !avant.getClass().isRecord()) {
            log.warn("recordUpdate ignore : avant/apres null ou pas du meme type record (module={}, entite={}/{})", module, entityName, entityId);
            return;
        }

        Map<String, Object> anciennesValeurs = new LinkedHashMap<>();
        Map<String, Object> nouvellesValeurs = new LinkedHashMap<>();
        List<String> champsModifies = diff(avant, apres, anciennesValeurs, nouvellesValeurs);
        if (champsModifies.isEmpty()) {
            return;
        }

        String description = buildDescription(entityName, champsModifies, anciennesValeurs, nouvellesValeurs);
        Contexte contexte = resolveContexte();
        dispatch(contexte, module, entityName, entityId, ActionAudit.UPDATE, description,
                toJson(anciennesValeurs), toJson(nouvellesValeurs), toJson(champsModifies), motif, StatutAudit.SUCCES, null);
    }

    private List<String> diff(Object avant, Object apres, Map<String, Object> anciennesValeursOut, Map<String, Object> nouvellesValeursOut) {
        List<String> champsModifies = new ArrayList<>();
        for (RecordComponent composant : avant.getClass().getRecordComponents()) {
            try {
                Object valeurAvant = composant.getAccessor().invoke(avant);
                Object valeurApres = composant.getAccessor().invoke(apres);
                if (!Objects.equals(valeurAvant, valeurApres)) {
                    champsModifies.add(composant.getName());
                    anciennesValeursOut.put(composant.getName(), masquerSiSensible(composant.getName(), valeurAvant));
                    nouvellesValeursOut.put(composant.getName(), masquerSiSensible(composant.getName(), valeurApres));
                }
            } catch (ReflectiveOperationException e) {
                log.debug("Impossible de lire le composant {} pour le diff d'audit: {}", composant.getName(), e.getMessage());
            }
        }
        return champsModifies;
    }

    private Object masquerSiSensible(String nomChamp, Object valeur) {
        return PayloadRedactor.isSensitiveFieldName(nomChamp) ? "***" : valeur;
    }

    private String buildDescription(String entityName, List<String> champsModifies, Map<String, Object> anciennesValeurs, Map<String, Object> nouvellesValeurs) {
        if (champsModifies.size() == 1) {
            String champ = champsModifies.get(0);
            return entityName + " modifie : " + champ + " passe de " + anciennesValeurs.get(champ) + " a " + nouvellesValeurs.get(champ);
        }
        return entityName + " modifie (" + champsModifies.size() + " champs) : " + String.join(", ", champsModifies);
    }

    private Contexte resolveContexte() {
        AuthenticatedUserPrincipal principal = resolvePrincipalSafely();
        String correlationId = MDC.get(AuditMdcKeys.CORRELATION_ID);
        String ipAddress = MDC.get(AuditMdcKeys.CLIENT_IP);
        CanalAudit canal = StringUtils.hasText(correlationId) ? CanalAudit.WEB : CanalAudit.SYSTEM;
        return new Contexte(principal, correlationId, ipAddress, canal);
    }

    private AuthenticatedUserPrincipal resolvePrincipalSafely() {
        try {
            return authService.currentPrincipal();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void dispatch(Contexte contexte, String module, String entityName, String entityId, ActionAudit action,
                           String description, String ancienneValeur, String nouvelleValeur, String champsModifies,
                           String motif, StatutAudit statut, String errorMessage) {
        String reference = codeGeneratorService.generate(CodePrefix.HISTORIQUE_MODIFICATION);
        historiqueModificationWriter.persistAsync(reference,
                contexte.principal() == null ? null : contexte.principal().userId(),
                contexte.principal() == null ? null : contexte.principal().username(),
                contexte.principal() == null ? null : contexte.principal().roles(),
                contexte.correlationId(), contexte.ipAddress(), contexte.canal(),
                module, entityName, entityId, action, description,
                ancienneValeur, nouvelleValeur, champsModifies, motif, statut, errorMessage);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Echec de serialisation JSON pour l'audit fonctionnel : {}", e.getMessage());
            return null;
        }
    }

    private record Contexte(AuthenticatedUserPrincipal principal, String correlationId, String ipAddress, CanalAudit canal) {
    }
}
