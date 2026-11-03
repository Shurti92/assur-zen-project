package ci.assurzen.service;

import ci.assurzen.dto.PublicContactInfo;
import ci.assurzen.services.DynamicConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordonnees de l'entreprise pour l'application mobile, sans compte requis (2026-11-03). Source unique : les cles de
 * configuration SUPPORT_* du back-office (ecran de configuration). Rien n'est ecrit en dur.
 */
@Service
@RequiredArgsConstructor
public class PublicContactService {

    private final DynamicConfigurationService configuration;

    public PublicContactInfo contact() {
        List<PublicContactInfo.Entry> telephones = new ArrayList<>();
        String principal = valeur("SUPPORT_TELEPHONE");
        if (principal != null) {
            telephones.add(new PublicContactInfo.Entry(valeur("SUPPORT_NOM"), principal));
        }
        String secondaire = valeur("SUPPORT_TELEPHONE_2");
        if (secondaire != null) {
            telephones.add(new PublicContactInfo.Entry(null, secondaire));
        }
        return new PublicContactInfo(telephones, valeur("SUPPORT_WHATSAPP"), valeur("SUPPORT_EMAIL"), valeur("SUPPORT_ADRESSE"),
                valeur("SUPPORT_HORAIRES"), urgences(valeur("SUPPORT_URGENCES")), valeur("SUPPORT_DISPONIBILITE"));
    }

    /** "SAMU:185;Pompiers:180" -&gt; deux entrees ; une entree sans libelle ("185") est acceptee. */
    static List<PublicContactInfo.Entry> urgences(String brut) {
        List<PublicContactInfo.Entry> resultat = new ArrayList<>();
        if (!StringUtils.hasText(brut)) {
            return resultat;
        }
        for (String element : brut.split(";")) {
            String e = element.trim();
            if (e.isEmpty()) {
                continue;
            }
            int separateur = e.lastIndexOf(':');
            if (separateur < 0) {
                resultat.add(new PublicContactInfo.Entry(null, e));
            } else {
                String numero = e.substring(separateur + 1).trim();
                String libelle = e.substring(0, separateur).trim();
                if (!numero.isEmpty()) {
                    resultat.add(new PublicContactInfo.Entry(libelle.isEmpty() ? null : libelle, numero));
                }
            }
        }
        return resultat;
    }

    private String valeur(String cle) {
        String valeur = configuration.getString(cle, null);
        return StringUtils.hasText(valeur) ? valeur.trim() : null;
    }
}
