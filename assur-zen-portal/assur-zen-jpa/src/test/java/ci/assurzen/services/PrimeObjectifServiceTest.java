package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.PrimeObjectifRequest;
import ci.assurzen.dto.PrimeObjectifResponse;
import ci.assurzen.entities.ExerciceComptable;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PrimeObjectif;
import ci.assurzen.entities.User;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.PrimeObjectifRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre le bornage de PrimeObjectif.tauxRealisation (DECIMAL(7,2)) dans demander() avant
 * persistance - meme bug reel du 2026-09-17 que CommercialPerformanceServiceTest
 * (recalculer_tauxAstronomique_...), constate ici sur le 2e point d'ecriture vulnerable trouve dans
 * le code (PartenaireService.computeTauxRealisation reutilise par ce service).
 */
class PrimeObjectifServiceTest {

    private record Fixture(
            PrimeObjectifService service,
            PrimeObjectifRepository repository,
            PartenaireRepository partenaireRepository,
            UserRepository userRepository,
            PartenaireService partenaireService,
            DynamicConfigurationService dynamicConfigurationService,
            AuthService authService
    ) {
    }

    private Fixture newFixture() {
        PrimeObjectifRepository repository = mock(PrimeObjectifRepository.class);
        PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PartenaireService partenaireService = mock(PartenaireService.class);
        DynamicConfigurationService dynamicConfigurationService = mock(DynamicConfigurationService.class);
        LedgerService ledgerService = mock(LedgerService.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AuthService authService = mock(AuthService.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);

        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        // Bareme par defaut = seuil de base 100% (meme convention que le seed data.sql).
        when(dynamicConfigurationService.getDecimal(eq("PRIME_OBJECTIF_SEUIL_BASE"))).thenReturn(BigDecimal.valueOf(100));
        when(dynamicConfigurationService.getDecimal(eq("PRIME_OBJECTIF_TAUX_BASE"))).thenReturn(BigDecimal.valueOf(5));
        when(dynamicConfigurationService.getDecimal(eq("PRIME_OBJECTIF_SEUIL_PALIER2"))).thenReturn(BigDecimal.valueOf(150));
        when(dynamicConfigurationService.getDecimal(eq("PRIME_OBJECTIF_TAUX_PALIER2"))).thenReturn(BigDecimal.valueOf(7));
        when(dynamicConfigurationService.getDecimal(eq("PRIME_OBJECTIF_SEUIL_PALIER3"))).thenReturn(BigDecimal.valueOf(200));
        when(dynamicConfigurationService.getDecimal(eq("PRIME_OBJECTIF_TAUX_PALIER3"))).thenReturn(BigDecimal.valueOf(10));

        when(codeGeneratorService.generate(eq(CodePrefix.PRIME_OBJECTIF))).thenReturn("PO-000001");

        AtomicLong idSeq = new AtomicLong(1);
        when(repository.save(any())).thenAnswer(inv -> {
            PrimeObjectif p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(idSeq.getAndIncrement());
            }
            return p;
        });

        PrimeObjectifService service = new PrimeObjectifService(
                repository, partenaireRepository, userRepository, partenaireService, dynamicConfigurationService,
                ledgerService, smsNotificationService, codeGeneratorService, authService, functionalAuditRecorder);

        return new Fixture(service, repository, partenaireRepository, userRepository, partenaireService,
                dynamicConfigurationService, authService);
    }

    private Partenaire partenaire(long id, BigDecimal objectifProduction) {
        Partenaire partenaire = Partenaire.builder()
                .id(id)
                .nom("Diallo")
                .prenoms("Awa")
                .telephone("0700000000")
                .objectifProduction(objectifProduction)
                .primeObjectifActivee(true)
                .exerciceComptable(ExerciceComptable.builder().id(9L).libelle("Exercice 2026").cloture(true).build())
                .build();
        return partenaire;
    }

    @Test
    void demander_tauxAstronomique_estBorneALaValeurMaxRepresentableParLaColonne() {
        Fixture fx = newFixture();
        // objectif=1, realise=100000 -> taux brut = 10 000 000% (10 millions), hors de portee de
        // DECIMAL(7,2) (max 99999.99), meme scenario reel que celui de CommercialPerformanceServiceTest.
        Partenaire partenaire = partenaire(5L, BigDecimal.ONE);
        when(fx.partenaireRepository().findById(5L)).thenReturn(Optional.of(partenaire));
        when(fx.partenaireService().computeObjectifRealise(partenaire)).thenReturn(BigDecimal.valueOf(100_000));
        when(fx.partenaireService().computeTauxRealisation(BigDecimal.valueOf(100_000), BigDecimal.ONE))
                .thenReturn(BigDecimal.valueOf(10_000_000));
        when(fx.repository().findByPartenaireIdAndExerciceComptableId(5L, 9L)).thenReturn(Optional.empty());

        PrimeObjectifResponse response = fx.service().demander(new PrimeObjectifRequest(5L));

        assertEquals(0, BigDecimal.valueOf(99999.99).compareTo(response.tauxRealisation()));
    }
}
