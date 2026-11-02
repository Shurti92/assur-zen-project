package ci.assurzen.services;

import ci.assurzen.entities.Langue;
import ci.assurzen.entities.TraductionEntry;
import ci.assurzen.repositories.LangueRepository;
import ci.assurzen.repositories.TraductionEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dictionnaire de traductions i18n runtime (voir docs/api-contracts/i18n.md du repo frontend).
 * Endpoints volontairement publics (pas d'auth) : la page de connexion elle-meme doit pouvoir
 * charger ses traductions avant que l'utilisateur soit authentifie (voir SecurityConfig).
 */
@Service
@RequiredArgsConstructor
public class I18nService {

    private final TraductionEntryRepository traductionEntryRepository;
    private final LangueRepository langueRepository;

    public Map<String, String> dictionnaire(String langueCode, String scope) {
        langueRepository.findByCode(langueCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Langue introuvable: " + langueCode));

        List<TraductionEntry> entrees = StringUtils.hasText(scope)
                ? traductionEntryRepository.findByLangue_CodeAndScopeOrderByCleAsc(langueCode, scope)
                : traductionEntryRepository.findByLangue_CodeOrderByCleAsc(langueCode);

        Map<String, String> dictionnaire = new LinkedHashMap<>();
        entrees.forEach(entree -> dictionnaire.put(entree.getCle(), entree.getValeur()));
        return dictionnaire;
    }

    public Map<String, Instant> version() {
        Map<String, Instant> versions = new LinkedHashMap<>();
        for (Langue langue : langueRepository.findAll()) {
            List<TraductionEntry> dernier = traductionEntryRepository.findTop1ByLangue_CodeOrderByLastUpdateDesc(langue.getCode());
            if (!dernier.isEmpty()) {
                versions.put(langue.getCode(), dernier.get(0).getLastUpdate());
            }
        }
        return versions;
    }
}
