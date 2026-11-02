package ci.assurzen.services;

import ci.assurzen.dto.SupportContactResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Contact d'aide affiche par l'Espace Partenaire (2026-10-04). Aucun gestionnaire n'est rattache aux
 * partenaires dans le modele : c'est le contact support general, regle par environnement depuis le
 * back-office (cles SUPPORT_*, cf. EspacePartenaireConfigInitializer). Valeur vide = null.
 */
@Service
@RequiredArgsConstructor
public class SupportContactService {

    private final DynamicConfigurationService configuration;

    public SupportContactResponse contact() {
        return new SupportContactResponse(
                valeur("SUPPORT_NOM"),
                valeur("SUPPORT_FONCTION"),
                valeur("SUPPORT_TELEPHONE"),
                valeur("SUPPORT_EMAIL"),
                valeur("SUPPORT_WHATSAPP"),
                valeur("SUPPORT_HORAIRES"),
                "SUPPORT");
    }

    private String valeur(String cle) {
        String valeur = configuration.getString(cle, null);
        return StringUtils.hasText(valeur) ? valeur.trim() : null;
    }
}
