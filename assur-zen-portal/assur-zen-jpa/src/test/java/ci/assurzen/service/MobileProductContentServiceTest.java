package ci.assurzen.service;

import ci.assurzen.dto.AdminMobileProductDtos;
import ci.assurzen.dto.PublicMobileDtos;
import ci.assurzen.entities.MobileProductContent;
import ci.assurzen.entities.Produit;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.repositories.MobileProductContentRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.CommercialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Catalogue mobile parametre depuis le back-office (2026-11-03). */
class MobileProductContentServiceTest {

    private ProduitRepository produits;
    private MobileProductContentRepository contenus;
    private MobileProductContentService service;

    private Produit produit(long id, String nom, boolean actif) {
        Produit p = new Produit();
        p.setId(id);
        p.setNomProduit(nom);
        p.setActive(actif);
        return p;
    }

    @BeforeEach
    void setUp() {
        produits = mock(ProduitRepository.class);
        contenus = mock(MobileProductContentRepository.class);
        AuthService auth = mock(AuthService.class);
        when(auth.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(29L, "admin", "ADMIN"));
        service = new MobileProductContentService(produits, contenus, mock(CommercialService.class), auth);
        when(contenus.save(any(MobileProductContent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AdminMobileProductDtos.ContentRequest requete(boolean visible, Integer ordre) {
        return new AdminMobileProductDtos.ContentRequest(visible, "Moto", "VEHICLE", "Protegez votre moto",
                List.of("Rapide", " ", "Sans paperasse"), "Souscrivez en 3 etapes", "Plus de 18 ans",
                List.of(new AdminMobileProductDtos.GuaranteeInput(null, "Responsabilite civile", "MANDATORY", "Dommages aux tiers", "Selon la loi",
                                List.of("tiers", "tous-risques"), null, null),
                        new AdminMobileProductDtos.GuaranteeInput("vol", "Vol", "OPTIONAL", null, "10 000 000 FCFA", List.of("tous-risques"), 5, null),
                        new AdminMobileProductDtos.GuaranteeInput("cache", "Garantie masquee", "INCLUDED", null, null, List.of(), 1, false)),
                List.of(new AdminMobileProductDtos.FormulaInput("tiers", "Tiers", "Responsabilite civile seule", 10, null),
                        new AdminMobileProductDtos.FormulaInput("tous-risques", "Tous risques", null, 20, true)),
                List.of("Conduite sans permis", " "), List.of("Carte grise", "Piece d'identite"),
                List.of(new AdminMobileProductDtos.FaqInput("Comment payer ?", "Par Wave.")), "moto", ordre, false);
    }

    @Test
    void enregistrer_puisLire_conserveLeContenuEtNettoieLesListes() {
        when(produits.findById(4L)).thenReturn(Optional.of(produit(4, "Assurance Moto", true)));
        when(contenus.findByProductId(4L)).thenReturn(Optional.empty());

        AdminMobileProductDtos.ProductContent c = service.enregistrer(4L, requete(true, 20));

        assertTrue(c.configured());
        assertTrue(c.visibleMobile());
        assertEquals("moto", c.productKey());
        assertEquals(List.of("Rapide", "Sans paperasse"), c.benefits());
        assertEquals("MANDATORY", c.guarantees().get(0).kind());
        assertEquals("g1", c.guarantees().get(0).id());
        assertEquals(List.of("Conduite sans permis"), c.exclusions());
        assertEquals(List.of("Carte grise", "Piece d'identite"), c.requiredDocuments());
        assertEquals("Par Wave.", c.faq().get(0).answer());
        assertEquals(2, c.formulas().size());
        assertEquals(20, c.displayOrder());
        assertEquals("admin", c.updatedBy());
    }

    @Test
    void formuleInconnueDansUneGarantie_estRefusee() {
        when(produits.findById(4L)).thenReturn(Optional.of(produit(4, "Assurance Moto", true)));
        when(contenus.findByProductId(4L)).thenReturn(Optional.empty());
        AdminMobileProductDtos.ContentRequest r = new AdminMobileProductDtos.ContentRequest(true, null, null, null, null, null, null,
                List.of(new AdminMobileProductDtos.GuaranteeInput("rc", "RC", "MANDATORY", null, null, List.of("inconnue"), null, null)),
                List.of(new AdminMobileProductDtos.FormulaInput("tiers", "Tiers", null, null, null)), null, null, null, null, null, null);
        assertEquals("VALIDATION_FAILED", assertThrows(MobileApiException.class, () -> service.enregistrer(4L, r)).getCode());
    }

    @Test
    void catalogue_publicFiltreLesGarantiesMasqueesEtDeriveLesGarantiesDesFormules() {
        when(produits.findById(4L)).thenReturn(Optional.of(produit(4, "Assurance Moto", true)));
        when(contenus.findByProductId(4L)).thenReturn(Optional.empty());
        MobileProductContent enregistre = new MobileProductContent();
        when(contenus.save(any(MobileProductContent.class))).thenAnswer(inv -> {
            MobileProductContent c = inv.getArgument(0);
            enregistre.setProductId(c.getProductId());
            enregistre.setVisibleMobile(c.isVisibleMobile());
            enregistre.setGuaranteesJson(c.getGuaranteesJson());
            enregistre.setFormulasJson(c.getFormulasJson());
            enregistre.setDisplayOrder(c.getDisplayOrder());
            enregistre.setUpdatedAt(c.getUpdatedAt());
            return c;
        });
        service.enregistrer(4L, requete(true, 20));
        when(contenus.findAll()).thenReturn(List.of(enregistre));
        when(produits.findAllById(any())).thenReturn(List.of(produit(4, "Assurance Moto", true)));

        PublicMobileDtos.Product p = service.produitsVisibles().items().get(0);

        assertEquals(List.of("vol", "g1"), p.guarantees().stream().map(PublicMobileDtos.Guarantee::id).toList());
        assertEquals(List.of("tiers", "tous-risques"), p.formulas().stream().map(PublicMobileDtos.Formula::id).toList());
        assertEquals(List.of("g1"), p.formulas().get(0).guaranteeIds());
        assertEquals(List.of("g1", "vol"), p.formulas().get(1).guaranteeIds().stream().sorted().toList());
        assertEquals("10 000 000 FCFA", p.guarantees().get(0).limit());
        assertTrue(p.contentVersion() != null);
    }

    @Test
    void produitSansContenu_estNonConfigureEtInvisible() {
        when(produits.findById(4L)).thenReturn(Optional.of(produit(4, "Assurance Moto", true)));
        when(contenus.findByProductId(4L)).thenReturn(Optional.empty());
        AdminMobileProductDtos.ProductContent c = service.lire(4L);
        assertFalse(c.configured());
        assertFalse(c.visibleMobile());
    }

    @Test
    void produitInconnu_donne404() {
        when(produits.findById(99L)).thenReturn(Optional.empty());
        assertEquals("PRODUCT_NOT_FOUND", assertThrows(MobileApiException.class, () -> service.enregistrer(99L, requete(true, 1))).getCode());
    }

    @Test
    void catalogue_neRenvoieQueLesProduitsActifsEtVisiblesTriesParOrdre() {
        MobileProductContent moto = contenu(4L, true, 20);
        MobileProductContent auto = contenu(1L, true, 10);
        MobileProductContent cache = contenu(3L, false, 5);
        MobileProductContent inactif = contenu(6L, true, 1);
        when(contenus.findAll()).thenReturn(List.of(moto, auto, cache, inactif));
        when(produits.findAllById(any())).thenReturn(List.of(produit(4, "Assurance Moto", true), produit(1, "Assurance Auto", true),
                produit(6, "Assurance Voyage", false)));

        List<PublicMobileDtos.Product> items = service.produitsVisibles().items();

        assertEquals(List.of("auto", "moto"), items.stream().map(PublicMobileDtos.Product::id).toList());
        assertEquals("Assurance Auto", items.get(0).title());
    }

    @Test
    void produitMasque_donne404EnLectureDirecte() {
        when(contenus.findAll()).thenReturn(List.of(contenu(3L, false, 5)));
        when(produits.findAllById(any())).thenReturn(List.of());
        assertEquals("PRODUCT_NOT_FOUND", assertThrows(MobileApiException.class, () -> service.produitVisible("habitation")).getCode());
    }

    private MobileProductContent contenu(long produitId, boolean visible, int ordre) {
        MobileProductContent c = new MobileProductContent();
        c.setProductId(produitId);
        c.setVisibleMobile(visible);
        c.setDisplayOrder(ordre);
        return c;
    }
}
