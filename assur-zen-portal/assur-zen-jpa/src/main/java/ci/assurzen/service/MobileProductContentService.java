package ci.assurzen.service;

import ci.assurzen.dto.AdminMobileProductDtos;
import ci.assurzen.dto.PublicMobileDtos;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.MobileProductContent;
import ci.assurzen.entities.Produit;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.MobileProductContentRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.services.CommercialService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contenu des produits affiche dans l'application mobile, parametrable depuis le back-office (2026-11-03). Lecture
 * publique : seuls les produits actifs ET marques visibles sur le mobile, tries par ordre d'affichage. Ecriture :
 * administrateurs uniquement. Aucun texte n'est ecrit en dur : ce que le back-office ne renseigne pas n'est pas renvoye.
 */
@Service
@RequiredArgsConstructor
public class MobileProductContentService {

    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type LISTE_TEXTES = new TypeToken<List<String>>() { }.getType();
    private static final java.lang.reflect.Type LISTE_GARANTIES = new TypeToken<List<AdminMobileProductDtos.Guarantee>>() { }.getType();
    private static final java.lang.reflect.Type LISTE_FORMULES = new TypeToken<List<AdminMobileProductDtos.Formula>>() { }.getType();
    private static final java.lang.reflect.Type LISTE_FAQ = new TypeToken<List<AdminMobileProductDtos.Faq>>() { }.getType();

    private final ProduitRepository produitRepository;
    private final MobileProductContentRepository contentRepository;
    private final CommercialService commercialService;
    private final ci.assurzen.service.AuthService authService;

    // ------------------------------------------------------------------ lecture publique

    @Transactional(readOnly = true)
    public PublicMobileDtos.ProductList produitsVisibles() {
        Map<Long, MobileProductContent> contenus = contentRepository.findAll().stream()
                .filter(MobileProductContent::isVisibleMobile)
                .collect(Collectors.toMap(MobileProductContent::getProductId, Function.identity(), (a, b) -> a));
        List<PublicMobileDtos.Product> items = produitRepository.findAllById(contenus.keySet()).stream()
                .filter(Produit::isActive)
                .map(p -> versPublic(p, contenus.get(p.getId())))
                .sorted(Comparator.comparingInt(PublicMobileDtos.Product::displayOrder).thenComparing(PublicMobileDtos.Product::title))
                .toList();
        return new PublicMobileDtos.ProductList(items);
    }

    @Transactional(readOnly = true)
    public PublicMobileDtos.Product produitVisible(String cle) {
        return produitsVisibles().items().stream().filter(p -> p.id().equalsIgnoreCase(cle)).findFirst()
                .orElseThrow(() -> new MobileApiException(404, "PRODUCT_NOT_FOUND", "Produit introuvable."));
    }

    /** Garanties parametrees pour un produit (detail d'un contrat), ou liste vide. */
    @Transactional(readOnly = true)
    public List<AdminMobileProductDtos.Guarantee> garanties(Long produitId) {
        if (produitId == null) {
            return List.of();
        }
        return contentRepository.findByProductId(produitId)
                .map(c -> garantiesVisibles(lireGaranties(c.getGuaranteesJson())))
                .orElse(List.of());
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public AdminMobileProductDtos.ProductContentList lister() {
        commercialService.requireAdmin();
        Map<Long, MobileProductContent> contenus = contentRepository.findAll().stream()
                .collect(Collectors.toMap(MobileProductContent::getProductId, Function.identity(), (a, b) -> a));
        return new AdminMobileProductDtos.ProductContentList(produitRepository.findAll().stream()
                .sorted(Comparator.comparing(Produit::getId))
                .map(p -> versAdmin(p, contenus.get(p.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminMobileProductDtos.ProductContent lire(Long produitId) {
        commercialService.requireAdmin();
        Produit produit = produit(produitId);
        return versAdmin(produit, contentRepository.findByProductId(produitId).orElse(null));
    }

    @Transactional
    public AdminMobileProductDtos.ProductContent enregistrer(Long produitId, AdminMobileProductDtos.ContentRequest r) {
        commercialService.requireAdmin();
        Produit produit = produit(produitId);
        MobileProductContent c = contentRepository.findByProductId(produitId).orElseGet(() -> {
            MobileProductContent nouveau = new MobileProductContent();
            nouveau.setProductId(produitId);
            return nouveau;
        });
        c.setVisibleMobile(Boolean.TRUE.equals(r.visibleMobile()));
        c.setShortName(vide(r.shortName()));
        c.setFamily(vide(r.family()));
        c.setDescription(vide(r.description()));
        c.setBenefitsJson(r.benefits() == null || r.benefits().isEmpty() ? null
                : GSON.toJson(r.benefits().stream().map(String::trim).filter(StringUtils::hasText).toList()));
        c.setHowItWorks(vide(r.howItWorks()));
        c.setEligibility(vide(r.eligibility()));
        List<AdminMobileProductDtos.Formula> formules = normaliserFormules(r.formulas());
        c.setFormulasJson(formules.isEmpty() ? null : GSON.toJson(formules));
        List<AdminMobileProductDtos.Guarantee> garanties = normaliserGaranties(r.guarantees(), formules);
        c.setGuaranteesJson(garanties.isEmpty() ? null : GSON.toJson(garanties));
        c.setExclusionsJson(nettoyer(r.exclusions()));
        c.setRequiredDocumentsJson(nettoyer(r.requiredDocuments()));
        c.setFaqJson(r.faq() == null || r.faq().isEmpty() ? null
                : GSON.toJson(r.faq().stream().map(f -> new AdminMobileProductDtos.Faq(f.question().trim(), f.answer().trim())).toList()));
        c.setIcon(vide(r.icon()));
        c.setDisplayOrder(r.displayOrder() == null ? 100 : r.displayOrder());
        c.setOnlineSubscription(Boolean.TRUE.equals(r.onlineSubscription()));
        c.setUpdatedAt(Instant.now());
        c.setUpdatedBy(authService.currentPrincipal().username());
        return versAdmin(produit, contentRepository.save(c));
    }

    /**
     * Fixe l'ordre d'affichage : le premier produit de la liste est affiche en premier (10, 20, 30...). Seuls les produits
     * qui ont deja un contenu mobile sont renumerotes : aucune ligne n'est CREEE pour un produit jamais renseigne (il resterait
     * sinon indistinguable d'un produit « renseigne puis masque »). Les produits jamais renseignes gardent leur place dans la
     * numerotation (le rang compte pour tous) mais n'ont rien a stocker.
     */
    @Transactional
    public AdminMobileProductDtos.ProductContentList ordonner(List<Long> produitIds) {
        commercialService.requireAdmin();
        int ordre = 10;
        for (Long id : produitIds) {
            produit(id);
            MobileProductContent c = contentRepository.findByProductId(id).orElse(null);
            if (c != null) {
                c.setDisplayOrder(ordre);
                c.setUpdatedAt(Instant.now());
                c.setUpdatedBy(authService.currentPrincipal().username());
                contentRepository.save(c);
            }
            ordre += 10;
        }
        return lister();
    }

    // ------------------------------------------------------------------

    private Produit produit(Long id) {
        return produitRepository.findById(id)
                .orElseThrow(() -> new MobileApiException(404, "PRODUCT_NOT_FOUND", "Produit introuvable.", "productId"));
    }

    private String vide(String valeur) {
        return StringUtils.hasText(valeur) ? valeur.trim() : null;
    }

    static String cle(Produit p) {
        Contract fictif = new Contract();
        fictif.setProduct(p);
        return MobileContractService.identifiantProduit(fictif);
    }

    private String nettoyer(List<String> textes) {
        if (textes == null) {
            return null;
        }
        List<String> propres = textes.stream().map(String::trim).filter(StringUtils::hasText).toList();
        return propres.isEmpty() ? null : GSON.toJson(propres);
    }

    private List<AdminMobileProductDtos.Formula> normaliserFormules(List<AdminMobileProductDtos.FormulaInput> entrees) {
        if (entrees == null) {
            return List.of();
        }
        java.util.Set<String> vus = new java.util.HashSet<>();
        List<AdminMobileProductDtos.Formula> resultat = new java.util.ArrayList<>();
        for (int i = 0; i < entrees.size(); i++) {
            AdminMobileProductDtos.FormulaInput f = entrees.get(i);
            if (!vus.add(f.id())) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Identifiant de formule en double : " + f.id(), "formulas[" + i + "].id");
            }
            resultat.add(new AdminMobileProductDtos.Formula(f.id(), f.name().trim(), vide(f.description()),
                    f.displayOrder() == null ? (i + 1) * 10 : f.displayOrder(), f.visible() == null || f.visible()));
        }
        return resultat;
    }

    private List<AdminMobileProductDtos.Guarantee> normaliserGaranties(List<AdminMobileProductDtos.GuaranteeInput> entrees,
                                                                       List<AdminMobileProductDtos.Formula> formules) {
        if (entrees == null) {
            return List.of();
        }
        java.util.Set<String> idsFormules = formules.stream().map(AdminMobileProductDtos.Formula::id).collect(Collectors.toSet());
        java.util.Set<String> vus = new java.util.HashSet<>();
        List<AdminMobileProductDtos.Guarantee> resultat = new java.util.ArrayList<>();
        for (int i = 0; i < entrees.size(); i++) {
            AdminMobileProductDtos.GuaranteeInput g = entrees.get(i);
            String id = StringUtils.hasText(g.id()) ? g.id() : "g" + (i + 1);
            if (!vus.add(id)) {
                throw new MobileApiException(400, "VALIDATION_FAILED", "Identifiant de garantie en double : " + id, "guarantees[" + i + "].id");
            }
            List<String> formulaIds = g.formulaIds() == null ? List.of() : g.formulaIds();
            for (String fid : formulaIds) {
                if (!idsFormules.contains(fid)) {
                    throw new MobileApiException(400, "VALIDATION_FAILED", "Formule inconnue : " + fid, "guarantees[" + i + "].formulaIds");
                }
            }
            resultat.add(new AdminMobileProductDtos.Guarantee(id, g.name().trim(), g.kind(), vide(g.description()), vide(g.limit()),
                    formulaIds, g.displayOrder() == null ? (i + 1) * 10 : g.displayOrder(), g.visible() == null || g.visible()));
        }
        return resultat;
    }

    private List<AdminMobileProductDtos.Guarantee> garantiesVisibles(List<AdminMobileProductDtos.Guarantee> garanties) {
        return garanties.stream().filter(g -> !Boolean.FALSE.equals(g.visible()))
                .sorted(Comparator.comparingInt(g -> g.displayOrder() == null ? 0 : g.displayOrder())).toList();
    }

    private List<AdminMobileProductDtos.Formula> lireFormules(String json) {
        return StringUtils.hasText(json) ? GSON.fromJson(json, LISTE_FORMULES) : List.of();
    }

    private List<AdminMobileProductDtos.Faq> lireFaq(String json) {
        return StringUtils.hasText(json) ? GSON.fromJson(json, LISTE_FAQ) : List.of();
    }

    private List<String> lireTextes(String json) {
        return StringUtils.hasText(json) ? GSON.fromJson(json, LISTE_TEXTES) : List.of();
    }

    private List<AdminMobileProductDtos.Guarantee> lireGaranties(String json) {
        return StringUtils.hasText(json) ? GSON.fromJson(json, LISTE_GARANTIES) : List.of();
    }

    private PublicMobileDtos.Product versPublic(Produit p, MobileProductContent c) {
        String cle = cle(p);
        String court = StringUtils.hasText(c.getShortName()) ? c.getShortName()
                : p.getNomProduit().replaceFirst("(?i)^assurance\\s+", "").trim();
        String famille = StringUtils.hasText(c.getFamily()) ? c.getFamily() : PublicCatalogService.famille(cle);
        List<AdminMobileProductDtos.Guarantee> garanties = garantiesVisibles(lireGaranties(c.getGuaranteesJson()));
        List<PublicMobileDtos.Formula> formules = lireFormules(c.getFormulasJson()).stream()
                .filter(f -> !Boolean.FALSE.equals(f.visible()))
                .sorted(Comparator.comparingInt(f -> f.displayOrder() == null ? 0 : f.displayOrder()))
                .map(f -> new PublicMobileDtos.Formula(f.id(), f.name(), f.description(), garanties.stream()
                        .filter(g -> g.formulaIds() != null && g.formulaIds().contains(f.id()))
                        .map(AdminMobileProductDtos.Guarantee::id).toList()))
                .toList();
        java.util.Set<String> formulesVisibles = formules.stream().map(PublicMobileDtos.Formula::id).collect(Collectors.toSet());
        return new PublicMobileDtos.Product(cle, p.getNomProduit(), court, c.getDescription(), famille, c.isOnlineSubscription(),
                lireTextes(c.getBenefitsJson()), c.getHowItWorks(), c.getEligibility(),
                garanties.stream().map(g -> new PublicMobileDtos.Guarantee(g.id(), g.name(), g.kind(), g.description(), g.limit(),
                        g.formulaIds() == null ? List.of() : g.formulaIds().stream().filter(formulesVisibles::contains).toList())).toList(),
                formules, lireTextes(c.getExclusionsJson()), lireTextes(c.getRequiredDocumentsJson()),
                lireFaq(c.getFaqJson()).stream().map(f -> new PublicMobileDtos.Faq(f.question(), f.answer())).toList(),
                c.getIcon(), c.getDisplayOrder(), c.getUpdatedAt());
    }

    private AdminMobileProductDtos.ProductContent versAdmin(Produit p, MobileProductContent c) {
        String cle = cle(p);
        if (c == null) {
            return new AdminMobileProductDtos.ProductContent(p.getId(), cle, p.getNomProduit(), p.isActive(), false, false, null,
                    PublicCatalogService.famille(cle), null, List.of(), null, null, List.of(), List.of(), List.of(), List.of(), List.of(), null, 100, false, null, null);
        }
        return new AdminMobileProductDtos.ProductContent(p.getId(), cle, p.getNomProduit(), p.isActive(), true, c.isVisibleMobile(),
                c.getShortName(), c.getFamily(), c.getDescription(), lireTextes(c.getBenefitsJson()), c.getHowItWorks(), c.getEligibility(),
                lireGaranties(c.getGuaranteesJson()), lireFormules(c.getFormulasJson()), lireTextes(c.getExclusionsJson()),
                lireTextes(c.getRequiredDocumentsJson()), lireFaq(c.getFaqJson()), c.getIcon(), c.getDisplayOrder(),
                c.isOnlineSubscription(), c.getUpdatedAt(), c.getUpdatedBy());
    }
}
