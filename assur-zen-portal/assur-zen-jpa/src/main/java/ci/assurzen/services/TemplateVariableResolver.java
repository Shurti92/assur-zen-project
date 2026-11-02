package ci.assurzen.services;

import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import org.springframework.stereotype.Service;

import static ci.assurzen.utils.SafeAccess.safe;

import java.util.Map;

/**
 * Resout les placeholders {{code}} d'un gabarit de template. Deux modes : resolution reelle
 * (Insured/Contract, au moment de l'envoi) et resolution d'apercu (valeurs d'exemple fournies par
 * l'appelant, pour la previsualisation d'un template avant activation).
 *
 * Extension directe de CampagneEnvoiService.personnaliser() deja en prod (qui gere {prenom}/{nom}
 * en dur pour les campagnes sans template) : {{...}} au lieu de {...} pour ne pas entrer en
 * collision avec ce format existant.
 */
@Service
public class TemplateVariableResolver {

    public String resoudre(String gabarit, Insured insured, Contract contract) {
        if (gabarit == null) {
            return null;
        }
        String resolved = gabarit;
        resolved = remplacer(resolved, "nom_client", insured == null ? null : safe(insured::getNom));
        resolved = remplacer(resolved, "prenom", insured == null ? null : safe(insured::getPrenoms));
        resolved = remplacer(resolved, "numero_contrat", contract == null ? null : safe(contract::getPolicyNumber));
        resolved = remplacer(resolved, "date_expiration", (contract == null || contract.getDateEcheance() == null) ? null : contract.getDateEcheance().toString());
        resolved = remplacer(resolved, "montant", (contract == null || contract.getPremium() == null) ? null : contract.getPremium().toString());
        resolved = remplacer(resolved, "compagnie", (contract == null || contract.getCompagnie() == null) ? null : safe(contract.getCompagnie()::getNom));
        // Pas de gestion vehicule/immatriculation dans le modele Contract actuel : reste vide tant qu'aucune donnee n'existe.
        resolved = remplacer(resolved, "immatriculation", null);
        return resolved;
    }

    public String resoudreAvecValeurs(String gabarit, Map<String, String> valeurs) {
        if (gabarit == null) {
            return null;
        }
        String resolved = gabarit;
        if (valeurs != null) {
            for (Map.Entry<String, String> entry : valeurs.entrySet()) {
                resolved = remplacer(resolved, entry.getKey(), entry.getValue());
            }
        }
        return resolved;
    }

    private String remplacer(String texte, String code, String valeur) {
        return texte.replace("{{" + code + "}}", valeur == null ? "" : valeur);
    }
}
