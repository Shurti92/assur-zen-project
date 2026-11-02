package ci.assurzen.services;

import ci.assurzen.dto.ContractRequest;
import ci.assurzen.entities.Compagnie;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireProduitCommission;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.ProduitCommissionCommercial;
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
import ci.assurzen.service.AuthService;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.audit.FunctionalAuditRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Commission partenaire (partnerShare) et commission commerciale - decisions utilisateur du
 * 2026-09-25 ("saisie 2 500, base 2 000") : saisie > valeur deja enregistree > configuration > 400
 * (partenaire) ; saisie > configuration commercial > commission commerciale du produit > 0 (commercial).
 */
class ContractsServiceCommissionPartenaireTest {

    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 9, 25);

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final ProduitRepository produitRepository = mock(ProduitRepository.class);
    private final InsuredRepository insuredRepository = mock(InsuredRepository.class);
    private final PartenaireRepository partenaireRepository = mock(PartenaireRepository.class);
    private final CompagnieRepository compagnieRepository = mock(CompagnieRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProduitCommissionCommercialRepository produitCommissionCommercialRepository = mock(ProduitCommissionCommercialRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final BusinessMetrics businessMetrics = mock(BusinessMetrics.class);
    private final PartenaireProduitCommissionService configuration = mock(PartenaireProduitCommissionService.class);

    private final ContractsService service = new ContractsService(
            contractRepository,
            produitRepository,
            insuredRepository,
            mock(VehiculeRepository.class),
            partenaireRepository,
            compagnieRepository,
            userRepository,
            mock(ProfileRepository.class),
            mock(ExerciceComptableRepository.class),
            mock(ProduitCommissionTauxRepository.class),
            produitCommissionCommercialRepository,
            mock(CodeGeneratorService.class),
            authService,
            businessMetrics,
            mock(LedgerService.class),
            mock(EcheancierService.class),
            mock(SmsNotificationService.class),
            mock(FideliteService.class),
            mock(FunctionalAuditRecorder.class),
            mock(DynamicConfigurationService.class),
            mock(ApplicationEventPublisher.class),
            configuration
    );

    private final Partenaire partenaire = Partenaire.builder().id(7L).type(PartenaireType.APPORTEUR).build();

    private PartenaireProduitCommission configurationDe(long montant) {
        return PartenaireProduitCommission.builder().id(1L).montant(montant).build();
    }

    // ---- Regle de priorite de la commission partenaire ----

    @Test
    void test1_commissionRecue2500_configuration3000_retient2500() {
        when(configuration.resoudrePourPartenaire(75L, partenaire, AUJOURD_HUI)).thenReturn(Optional.of(configurationDe(3000)));

        assertEquals(2500L, service.resolvePartnerShare(75L, partenaire, 2500L, null, AUJOURD_HUI));
    }

    @Test
    void test2_commissionAbsente_configuration3000_retient3000() {
        when(configuration.resoudrePourPartenaire(75L, partenaire, AUJOURD_HUI)).thenReturn(Optional.of(configurationDe(3000)));

        assertEquals(3000L, service.resolvePartnerShare(75L, partenaire, null, null, AUJOURD_HUI));
    }

    @Test
    void test3_commissionAbsente_sansConfiguration_estRejeteeAvec400() {
        when(configuration.resoudrePourPartenaire(75L, partenaire, AUJOURD_HUI)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolvePartnerShare(75L, partenaire, null, null, AUJOURD_HUI));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("partnerShare est obligatoire"));
    }

    @Test
    void modification_commissionAbsente_conserveLaValeurDejaEnregistreePlutotQueLaConfiguration() {
        when(configuration.resoudrePourPartenaire(75L, partenaire, AUJOURD_HUI)).thenReturn(Optional.of(configurationDe(3000)));

        assertEquals(2500L, service.resolvePartnerShare(75L, partenaire, null, 2500L, AUJOURD_HUI));
    }

    @Test
    void venteDirecte_sansPartenaire_commissionPartenaireA0() {
        assertEquals(0L, service.resolvePartnerShare(75L, null, 2500L, null, AUJOURD_HUI));
    }

    // ---- Bornes des montants saisis ----

    @Test
    void montantSaisiNegatif_estRejeteAvec400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validerMontantsSaisis(requete(4500L, 2500L, -1L, null)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("partnerShare doit etre compris entre 0 et la prime"));
    }

    @Test
    void montantSaisiSuperieurALaPrime_estRejeteAvec400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validerMontantsSaisis(requete(4500L, 5000L, 2500L, null)));
        assertTrue(ex.getReason().startsWith("commissionAmount doit etre compris entre 0 et la prime"));
    }

    // ---- Commission commerciale ----

    @Test
    void commissionCommercialeSaisie_estConserveeEtLeTauxEnEstDerive() {
        when(configuration.resoudrePourCommercial(75L, 3L, AUJOURD_HUI)).thenReturn(Optional.of(configurationDe(900)));

        ContractsService.CommissionResolution r = service.resolveCommercialCommission(75L, null, 10000L, 1500L, 3L, null, AUJOURD_HUI);

        assertEquals(1500L, r.commissionAmount());
        assertEquals(0, new BigDecimal("0.1500").compareTo(r.commissionRate()));
    }

    @Test
    void commissionCommercialeAbsente_configurationDuCommercialPrioritaireSurLaCommissionDuProduit() {
        when(configuration.resoudrePourCommercial(75L, 3L, AUJOURD_HUI)).thenReturn(Optional.of(configurationDe(900)));

        assertEquals(900L, service.resolveCommercialCommission(75L, null, 10000L, null, 3L, null, AUJOURD_HUI).commissionAmount());
    }

    @Test
    void commissionCommercialeAbsente_sansConfigurationDuCommercial_retombeSurLaCommissionDuProduit() {
        when(configuration.resoudrePourCommercial(75L, 3L, AUJOURD_HUI)).thenReturn(Optional.empty());
        when(produitCommissionCommercialRepository.findByProduitId(75L)).thenReturn(Optional.of(
                ProduitCommissionCommercial.builder().tauxCommission(new BigDecimal("10")).libTauxCommission("%").active(true).build()));

        assertEquals(1000L, service.resolveCommercialCommission(75L, null, 10000L, null, 3L, null, AUJOURD_HUI).commissionAmount());
    }

    @Test
    void commissionCommercialeNonNulleAvecPartenaire_estRejeteeAvec400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveCommercialCommission(75L, partenaire, 10000L, 1500L, 3L, null, AUJOURD_HUI));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    // ---- Test 4 : creation mono de bout en bout ----

    @Test
    @SuppressWarnings("unchecked")
    void test4_creationMono_commissionPartenaire2500_estPersisteeA2500() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(3L, "admin", "ADMIN"));
        when(businessMetrics.recordSubscriptionProcessing(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(utilisateur(3L)));
        when(partenaireRepository.findById(7L)).thenReturn(Optional.of(partenaire));
        when(produitRepository.findById(75L)).thenReturn(Optional.of(Produit.builder().id(75L).build()));
        when(insuredRepository.findById(10L)).thenReturn(Optional.of(Insured.builder().id(10L).build()));
        when(compagnieRepository.findById(2L)).thenReturn(Optional.of(Compagnie.builder().id(2L).build()));
        when(configuration.resoudrePourPartenaire(eq(75L), eq(partenaire), any())).thenReturn(Optional.of(configurationDe(3000)));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(requete(4500L, 3000L, 2500L, "POL-TEST-1"));

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contractRepository).save(captor.capture());
        assertEquals(2500L, captor.getValue().getPartnerShare());
        assertEquals(3000L, captor.getValue().getCommissionAmount());
    }

    // ---- Part partenaire <= commission du contrat (regle du 2026-09-25) ----

    @Test
    void creation_partSaisieSuperieureALaCommission_estRejeteeAvec400() {
        preparerCreation();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(requete(4500L, 2000L, 2500L, "POL-TEST-2")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("partnerShare (2 500 FCFA) ne peut pas depasser la commission du contrat (2 000 FCFA)", ex.getReason());
    }

    @Test
    void creation_partConfigureeSuperieureALaCommission_estRejeteeAvec400() {
        preparerCreation();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(requete(4500L, 2000L, null, "POL-TEST-3")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().startsWith("La commission partenaire configuree pour ce produit (3 000 FCFA) depasse la commission du contrat (2 000 FCFA)"));
    }

    @Test
    void partEgaleALaCommission_estAcceptee() {
        ContractsService.verifierPartSousCommission(2000L, 2000L, ContractsService.OriginePart.SAISIE);
    }

    @SuppressWarnings("unchecked")
    private void preparerCreation() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(3L, "admin", "ADMIN"));
        when(businessMetrics.recordSubscriptionProcessing(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(utilisateur(3L)));
        when(partenaireRepository.findById(7L)).thenReturn(Optional.of(partenaire));
        when(configuration.resoudrePourPartenaire(eq(75L), eq(partenaire), any())).thenReturn(Optional.of(configurationDe(3000)));
    }

    private static User utilisateur(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private ContractRequest requete(Long premium, Long commissionAmount, Long partnerShare, String policyNumber) {
        return new ContractRequest(policyNumber, AUJOURD_HUI, "12 mois", AUJOURD_HUI.plusYears(1), 75L, 10L, null, 7L, 2L,
                premium, new BigDecimal("0.4444"), commissionAmount, partnerShare, ContractStatus.draft, null, null, 3L,
                null, null, null, null, null, null, null, null, null);
    }
}
