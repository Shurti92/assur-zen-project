package ci.assurzen.services;

import ci.assurzen.dto.ProduitCommissionCommercialRequest;
import ci.assurzen.dto.ProduitCommissionCommercialResponse;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ProduitCommissionCommercial;
import ci.assurzen.repositories.ProduitCommissionCommercialRepository;
import ci.assurzen.repositories.ProduitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Couvre l'unicite (un seul taux/montant par produit, pas de dimension type de partenaire
 * contrairement a ProduitCommissionTaux) et la resolution introuvable-produit. */
class ProduitCommissionCommercialServiceTest {

    private ProduitCommissionCommercialRepository repository;
    private ProduitRepository produitRepository;
    private ProduitCommissionCommercialService service;

    private void newFixture() {
        repository = mock(ProduitCommissionCommercialRepository.class);
        produitRepository = mock(ProduitRepository.class);
        service = new ProduitCommissionCommercialService(repository, produitRepository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Produit produit(long id, String nom) {
        Produit p = new Produit();
        p.setId(id);
        p.setNomProduit(nom);
        return p;
    }

    @Test
    void create_succes_persisteLeTauxPourCeProduit() {
        newFixture();
        Produit p = produit(1L, "Assurance Auto");
        when(produitRepository.findById(1L)).thenReturn(Optional.of(p));
        when(repository.findByProduitId(1L)).thenReturn(Optional.empty());

        ProduitCommissionCommercialResponse response = service.create(
                new ProduitCommissionCommercialRequest(1L, BigDecimal.valueOf(10), "%", true));

        assertEquals(1L, response.produitId());
        assertEquals("Assurance Auto", response.produitName());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(response.tauxCommission()));
    }

    @Test
    void create_commissionDejaExistantePourCeProduit_estRejeteeAvec409() {
        newFixture();
        Produit p = produit(1L, "Assurance Auto");
        when(produitRepository.findById(1L)).thenReturn(Optional.of(p));
        ProduitCommissionCommercial existante = ProduitCommissionCommercial.builder().id(5L).produit(p)
                .tauxCommission(BigDecimal.TEN).libTauxCommission("%").active(true).build();
        when(repository.findByProduitId(1L)).thenReturn(Optional.of(existante));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new ProduitCommissionCommercialRequest(1L, BigDecimal.valueOf(15), "%", true)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void create_produitIntrouvable_estRejeteeAvec404() {
        newFixture();
        when(produitRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new ProduitCommissionCommercialRequest(99L, BigDecimal.TEN, "%", true)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void update_versUnProduitDejaConfigure_estRejeteAvec409() {
        newFixture();
        Produit p1 = produit(1L, "Assurance Auto");
        Produit p2 = produit(2L, "Assurance Moto");
        ProduitCommissionCommercial existante = ProduitCommissionCommercial.builder().id(5L).produit(p1)
                .tauxCommission(BigDecimal.TEN).libTauxCommission("%").active(true).build();
        ProduitCommissionCommercial autre = ProduitCommissionCommercial.builder().id(6L).produit(p2)
                .tauxCommission(BigDecimal.valueOf(20000)).libTauxCommission("FCFA").active(true).build();
        when(repository.findById(5L)).thenReturn(Optional.of(existante));
        when(produitRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(repository.findByProduitId(2L)).thenReturn(Optional.of(autre));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.update(5L,
                new ProduitCommissionCommercialRequest(2L, BigDecimal.TEN, "%", true)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void create_libelleLibreAutreQuePourcentOuFcfa_estRejeteAvec400() {
        // Decision utilisateur du 2026-09-25 : "10%" aurait ete traite comme un montant fixe de 10 FCFA.
        newFixture();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new ProduitCommissionCommercialRequest(1L, BigDecimal.TEN, "10%", true)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("libTauxCommission doit valoir \"%\" (pourcentage de la prime) ou \"FCFA\" (montant fixe), recu : \"10%\"", ex.getReason());
    }

    @Test
    void create_montantFixeFcfa_estAccepte() {
        newFixture();
        when(produitRepository.findById(1L)).thenReturn(java.util.Optional.of(produit(1L, "Moto")));

        assertEquals("FCFA", service.create(new ProduitCommissionCommercialRequest(1L, BigDecimal.valueOf(500), "FCFA", true)).libTauxCommission());
    }
}
