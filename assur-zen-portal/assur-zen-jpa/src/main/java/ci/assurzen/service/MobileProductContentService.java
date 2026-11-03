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
        return contentRepository.findByProductId(produitId).map(c -> lireGaranties(c.getGuaranteesJson())).orElse(List.of());
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
        c.setGuaranteesJson(r.guarantees() == null || r.guarantees().isEmpty() ? null
                : GSON.toJson(r.guarantees().stream().map(g -> new AdminMobileProductDtos.Guarantee(g.name().trim(), g.kind())).toList()));
        c.setIcon(vide(r.icon()));
        c.setDisplayOrder(r.displayOrder() == null ? 100 : r.displayOrder());
        c.setOnlineSubscription(Boolean.TRUE.equals(r.onlineSubscription()));
        c.setUpdatedAt(Instant.now());
        c.setUpdatedBy(authService.currentPrincipal().username());
        return versAdmin(produit, contentRepository.save(c));
    }

    /** Fixe l'ordre d'affichage : le premier produit de la liste est affiche en premier (10, 20, 30...). */
    @Transactional
    public AdminMobileProductDtos.ProductContentList ordonner(List<Long> produitIds) {
        commercialService.requireAdmin();
        int ordre = 10;
        for (Long id : produitIds) {
            produit(id);
            MobileProductContent c = contentRepository.findByProductId(id).orElseGet(() -> {
                MobileProductContent nouveau = new MobileProductContent();
                nouveau.setProductId(id);
                return nouveau;
            });
            c.setDisplayOrder(ordre);
            c.setUpdatedAt(Instant.now());
            c.setUpdatedBy(authService.currentPrincipal().username());
            contentRepository.save(c);
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
        return new PublicMobileDtos.Product(cle, p.getNomProduit(), court, c.getDescription(), famille, c.isOnlineSubscription(),
                lireTextes(c.getBenefitsJson()), c.getHowItWorks(), c.getEligibility(),
                lireGaranties(c.getGuaranteesJson()).stream().map(g -> new PublicMobileDtos.Guarantee(g.name(), g.kind())).toList(),
                c.getIcon(), c.getDisplayOrder());
    }

    private AdminMobileProductDtos.ProductContent versAdmin(Produit p, MobileProductContent c) {
        String cle = cle(p);
        if (c == null) {
            return new AdminMobileProductDtos.ProductContent(p.getId(), cle, p.getNomProduit(), p.isActive(), false, false, null,
                    PublicCatalogService.famille(cle), null, List.of(), null, null, List.of(), null, 100, false, null, null);
        }
        return new AdminMobileProductDtos.ProductContent(p.getId(), cle, p.getNomProduit(), p.isActive(), true, c.isVisibleMobile(),
                c.getShortName(), c.getFamily(), c.getDescription(), lireTextes(c.getBenefitsJson()), c.getHowItWorks(), c.getEligibility(),
                lireGaranties(c.getGuaranteesJson()), c.getIcon(), c.getDisplayOrder(), c.isOnlineSubscription(), c.getUpdatedAt(), c.getUpdatedBy());
    }
}
