package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.StatutReversementPrime;
import ci.assurzen.entities.User;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.CompagnieRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.ExerciceComptableRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.ProduitCommissionCommercialRepository;
import ci.assurzen.repositories.ProduitCommissionTauxRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VehiculeRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reversement de la prime a la compagnie etendu aux ventes directes d'un commercial interne
 * (demande frontend du 2026-09-25) : partenaire OU vendeur ayant le role COMMERCIAL - une vente saisie
 * par un administrateur (salesRep renseigne sur tous les contrats) n'est pas concernee.
 */
class ContractsServicePrimeReversementTest {

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final AuthService authService = mock(AuthService.class);

    private final ContractsService service = new ContractsService(
            contractRepository,
            mock(ProduitRepository.class),
            mock(InsuredRepository.class),
            mock(VehiculeRepository.class),
            mock(PartenaireRepository.class),
            mock(CompagnieRepository.class),
            mock(UserRepository.class),
            mock(ProfileRepository.class),
            mock(ExerciceComptableRepository.class),
            mock(ProduitCommissionTauxRepository.class),
            mock(ProduitCommissionCommercialRepository.class),
            mock(CodeGeneratorService.class),
            authService,
            mock(BusinessMetrics.class),
            mock(LedgerService.class),
            mock(EcheancierService.class),
            mock(SmsNotificationService.class),
            mock(FideliteService.class),
            mock(FunctionalAuditRecorder.class),
            mock(DynamicConfigurationService.class),
            mock(ApplicationEventPublisher.class),
            mock(PartenaireProduitCommissionService.class)
    );

    @BeforeEach
    void setUp() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User utilisateur(long id, String roles) {
        User user = new User();
        user.setId(id);
        user.setRoles(roles);
        return user;
    }

    private static Contract contrat(long id, Partenaire partenaire, User vendeur, StatutReversementPrime statut) {
        return Contract.builder().id(id).policyNumber("CTR-" + id).partenaire(partenaire).salesRep(vendeur)
                .status(ContractStatus.signed).primeReversementStatus(statut).createdAt(Instant.now()).build();
    }

    @Test
    void intermediaire_partenaireOuCommercial_maisPasUneVenteSaisieParUnAdmin() {
        assertTrue(ContractsService.aUnIntermediaire(contrat(1, Partenaire.builder().id(7L).build(), utilisateur(1, "ADMIN"), null)));
        assertTrue(ContractsService.aUnIntermediaire(contrat(2, null, utilisateur(50, "COMMERCIAL"), null)));
        assertFalse(ContractsService.aUnIntermediaire(contrat(3, null, utilisateur(1, "ADMIN"), null)));
        assertFalse(ContractsService.aUnIntermediaire(contrat(4, null, null, null)));
    }

    /** Creation sans statut fourni (decision utilisateur du 2026-09-25) : non_reverse s'il y a un
     * intermediaire qui doit reverser la prime, reverse sinon. */
    @Test
    void statutParDefautALaCreation_nonReverseAvecIntermediaire_reverseSans() {
        assertEquals(StatutReversementPrime.non_reverse, ContractsService.statutReversementParDefaut(
                contrat(1, Partenaire.builder().id(7L).build(), utilisateur(1, "ADMIN"), null)));
        assertEquals(StatutReversementPrime.non_reverse, ContractsService.statutReversementParDefaut(
                contrat(2, null, utilisateur(50, "COMMERCIAL"), null)));
        assertEquals(StatutReversementPrime.reverse, ContractsService.statutReversementParDefaut(
                contrat(3, null, utilisateur(1, "ADMIN"), null)));
    }

    @Test
    void reverserPrime_venteDirecteDUnCommercial_estAcceptee() {
        Contract contrat = contrat(10, null, utilisateur(50, "COMMERCIAL"), StatutReversementPrime.non_reverse);
        when(contractRepository.findById(10L)).thenReturn(Optional.of(contrat));

        service.reverserPrime(10L);

        assertEquals(StatutReversementPrime.reverse, contrat.getPrimeReversementStatus());
    }

    @Test
    void reverserPrime_sansPartenaireNiCommercial_estRefuseAvec409() {
        when(contractRepository.findById(11L)).thenReturn(Optional.of(
                contrat(11, null, utilisateur(1, "ADMIN"), StatutReversementPrime.non_reverse)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.reverserPrime(11L));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Contrat CTR-11 n'est rattache ni a un partenaire ni a un commercial", ex.getReason());
    }

    @Test
    void primesNonReversees_incluentLesVentesDesCommerciauxMaisPasCellesDesAdmins() {
        when(contractRepository.findAll(any(Sort.class))).thenReturn(List.of(
                contrat(20, Partenaire.builder().id(7L).build(), utilisateur(1, "ADMIN"), StatutReversementPrime.non_reverse),
                contrat(21, null, utilisateur(50, "COMMERCIAL"), StatutReversementPrime.non_reverse),
                contrat(22, null, utilisateur(1, "ADMIN"), StatutReversementPrime.non_reverse),
                contrat(23, null, utilisateur(50, "COMMERCIAL"), StatutReversementPrime.reverse)));

        List<Long> ids = service.findPrimeNonReverseeGlobal(0, 20).content().stream().map(c -> c.id()).toList();

        assertEquals(List.of(20L, 21L), ids);
    }
}
