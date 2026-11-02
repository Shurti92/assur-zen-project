package ci.assurzen.services;

import ci.assurzen.dto.PartenaireProduitCommissionRequest;
import ci.assurzen.entities.BeneficiaireCommission;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireProduitCommission;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.entities.Produit;
import ci.assurzen.repositories.PartenaireProduitCommissionRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartenaireProduitCommissionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    private final PartenaireProduitCommissionRepository repository = mock(PartenaireProduitCommissionRepository.class);
    private final ProduitRepository produitRepository = mock(ProduitRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final PartenaireProduitCommissionService service = new PartenaireProduitCommissionService(
            repository, produitRepository, partenaireRepository, mock(UserRepository.class));

    private final Produit produit = Produit.builder().id(75L).build();
    private final Partenaire partenaire = Partenaire.builder().id(7L).type(PartenaireType.APPORTEUR).build();

    private PartenaireProduitCommission config(long id, Partenaire p, PartenaireType type, long montant,
                                               LocalDate debut, LocalDate fin) {
        return PartenaireProduitCommission.builder().id(id).produit(produit).beneficiaire(BeneficiaireCommission.PARTENAIRE)
                .partenaire(p).typePartenaire(type).montant(montant).active(true).dateDebut(debut).dateFin(fin).build();
    }

    @Test
    void partenairePrecis_estPrioritaireSurSonType() {
        when(repository.findByProduitId(75L)).thenReturn(List.of(
                config(1L, null, PartenaireType.APPORTEUR, 3000L, null, null),
                config(2L, partenaire, null, 2500L, null, null)));

        assertEquals(2500L, service.resoudrePourPartenaire(75L, partenaire, DATE).orElseThrow().getMontant());
    }

    @Test
    void sansConfigurationPrecise_retombeSurLeTypeDuPartenaire() {
        when(repository.findByProduitId(75L)).thenReturn(List.of(
                config(1L, null, PartenaireType.APPORTEUR, 3000L, null, null),
                config(3L, null, PartenaireType.AGENT, 9999L, null, null)));

        assertEquals(3000L, service.resoudrePourPartenaire(75L, partenaire, DATE).orElseThrow().getMontant());
    }

    @Test
    void configurationHorsPeriode_estIgnoree() {
        when(repository.findByProduitId(75L)).thenReturn(List.of(
                config(2L, partenaire, null, 2500L, DATE.plusDays(1), null)));

        assertTrue(service.resoudrePourPartenaire(75L, partenaire, DATE).isEmpty());
    }

    @Test
    void creation_chevauchantUneConfigurationActiveDeMemeCle_estRejeteeAvec409() {
        when(produitRepository.findById(75L)).thenReturn(Optional.of(produit));
        when(partenaireRepository.findById(7L)).thenReturn(Optional.of(partenaire));
        when(repository.findByProduitId(75L)).thenReturn(List.of(config(2L, partenaire, null, 2500L, null, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new PartenaireProduitCommissionRequest(75L, BeneficiaireCommission.PARTENAIRE, 7L, null, null, 2600L, true, DATE, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void partenaireEtTypeEnsemble_estRejeteAvec400() {
        when(produitRepository.findById(75L)).thenReturn(Optional.of(produit));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.create(
                new PartenaireProduitCommissionRequest(75L, BeneficiaireCommission.PARTENAIRE, 7L, PartenaireType.APPORTEUR, null, 2600L, true, null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void creation_renvoieLeNomEtLesPrenomsDuPartenaire() {
        Partenaire kouassi = Partenaire.builder().id(7L).nom("Kouassi").prenoms("Serge").type(PartenaireType.DIRECT).build();
        when(produitRepository.findById(75L)).thenReturn(Optional.of(produit));
        when(partenaireRepository.findById(7L)).thenReturn(Optional.of(kouassi));
        when(repository.findByProduitId(75L)).thenReturn(List.of());
        when(repository.save(any(PartenaireProduitCommission.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("Kouassi Serge", service.create(
                new PartenaireProduitCommissionRequest(75L, BeneficiaireCommission.PARTENAIRE, 7L, null, null, 2600L, true, DATE, null))
                .partenaireNom());
    }
}
