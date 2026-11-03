package ci.assurzen.service;

import ci.assurzen.dto.PublicMobileDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Catalogue des produits pour l'application mobile, lisible sans compte (demande n°5, 2026-11-03). Le contenu est
 * parametre depuis le back-office (MobileProductContentService) : seuls les produits actifs marques visibles sur le
 * mobile sont renvoyes. Aucun texte n'est ecrit en dur.
 */
@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private final MobileProductContentService contenu;

    public PublicMobileDtos.ProductList produits() {
        return contenu.produitsVisibles();
    }

    public PublicMobileDtos.Product produit(String id) {
        return contenu.produitVisible(id);
    }

    /** Famille deduite de l'identifiant du produit quand le back-office n'en a pas renseigne. */
    static String famille(String id) {
        String p = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (p.contains("auto") || p.contains("moto") || p.contains("vtc") || p.contains("vehicule") || p.contains("flotte")) {
            return "VEHICLE";
        }
        if (p.contains("sante") || p.contains("vie") || p.contains("voyage") || p.contains("scolaire")) {
            return "PERSON";
        }
        if (p.contains("habitation")) {
            return "PROPERTY";
        }
        if (p.contains("entreprise") || p.contains("transport") || p.contains("marchandises")) {
            return "PROFESSIONAL";
        }
        return null;
    }
}
