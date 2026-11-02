package ci.assurzen.services;

import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireType;
import ci.assurzen.entities.ProduitCommissionTaux;
import ci.assurzen.dto.ContractResponse;
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
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre resolveCommission() - correctif du 2026-09-09 (bug reel signale par l'utilisateur) : quand un
 * SEUL des deux champs (commissionRate ou commissionAmount) est fourni, l'autre doit se deriver de
 * CELUI fourni, jamais du taux configure dans t_assur_zen_produit_commission_taux. Methode privee,
 * invoquee par reflexion pour l'isoler du reste du (tres gros) service.
 */
class ContractsServiceTest {

    private record Fixture(
            ContractsService service,
            ProduitCommissionTauxRepository produitCommissionTauxRepository,
            ContractRepository contractRepository,
            SmsNotificationService smsNotificationService,
            AuthService authService
    ) {
    }

    private Fixture newFixture() {
        ProduitCommissionTauxRepository produitCommissionTauxRepository = mock(ProduitCommissionTauxRepository.class);
        ContractRepository contractRepository = mock(ContractRepository.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        AuthService authService = mock(AuthService.class);

        ContractsService service = new ContractsService(
                contractRepository,
                mock(ProduitRepository.class),
                mock(InsuredRepository.class),
                mock(VehiculeRepository.class),
                mock(PartenaireRepository.class),
                mock(CompagnieRepository.class),
                mock(UserRepository.class),
                mock(ProfileRepository.class),
                mock(ExerciceComptableRepository.class),
                produitCommissionTauxRepository,
                mock(ProduitCommissionCommercialRepository.class),
                mock(CodeGeneratorService.class),
                authService,
                mock(BusinessMetrics.class),
                mock(LedgerService.class),
                mock(EcheancierService.class),
                smsNotificationService,
                mock(FideliteService.class),
                mock(FunctionalAuditRecorder.class),
                mock(DynamicConfigurationService.class),
                mock(ApplicationEventPublisher.class),
                mock(PartenaireProduitCommissionService.class)
        );

        return new Fixture(service, produitCommissionTauxRepository, contractRepository, smsNotificationService, authService);
    }

    private ContractResponse toResponse(ContractsService service, Contract contract) throws Exception {
        Method m = ContractsService.class.getDeclaredMethod("toResponse", Contract.class);
        m.setAccessible(true);
        try {
            return (ContractResponse) m.invoke(service, contract);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private Partenaire partenaire(long id, PartenaireType type) {
        return Partenaire.builder().id(id).type(type).build();
    }

    /** resolveCommission(Long productId, Partenaire partenaire, Long premium, BigDecimal requestedRate, Long requestedAmount) */
    private Object resolveCommission(ContractsService service, Long productId, Partenaire partenaire, Long premium,
                                      BigDecimal requestedRate, Long requestedAmount) throws Exception {
        Method m = ContractsService.class.getDeclaredMethod("resolveCommission",
                Long.class, Partenaire.class, Long.class, BigDecimal.class, Long.class);
        m.setAccessible(true);
        try {
            return m.invoke(service, productId, partenaire, premium, requestedRate, requestedAmount);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }

    private BigDecimal rateOf(Object commissionResolution) throws Exception {
        return (BigDecimal) commissionResolution.getClass().getDeclaredMethod("commissionRate").invoke(commissionResolution);
    }

    private Long amountOf(Object commissionResolution) throws Exception {
        return (Long) commissionResolution.getClass().getDeclaredMethod("commissionAmount").invoke(commissionResolution);
    }

    @Test
    void resolveCommission_tauxSeulFourni_deriveLeMontantDepuisCeTauxPasDeLaTable() throws Exception {
        Fixture fx = newFixture();
        Partenaire partenaire = partenaire(1L, PartenaireType.AGENT);

        Object resolution = resolveCommission(fx.service(), 1L, partenaire, 500_000L, new BigDecimal("0.15"), null);

        assertEquals(0, new BigDecimal("0.15").compareTo(rateOf(resolution)));
        assertEquals(75_000L, amountOf(resolution));
        verify(fx.produitCommissionTauxRepository(), never()).findByProduitIdAndTypePartenaire(any(), any());
    }

    @Test
    void resolveCommission_montantSeulFourni_deriveLeTauxDepuisCeMontantPasDeLaTable() throws Exception {
        Fixture fx = newFixture();
        Partenaire partenaire = partenaire(1L, PartenaireType.AGENT);

        Object resolution = resolveCommission(fx.service(), 1L, partenaire, 500_000L, null, 75_000L);

        assertEquals(0, new BigDecimal("0.1500").compareTo(rateOf(resolution)));
        assertEquals(75_000L, amountOf(resolution));
        verify(fx.produitCommissionTauxRepository(), never()).findByProduitIdAndTypePartenaire(any(), any());
    }

    @Test
    void resolveCommission_lesDeuxFournis_respecteLesDeuxSansConsulterLaTable() throws Exception {
        Fixture fx = newFixture();
        Partenaire partenaire = partenaire(1L, PartenaireType.AGENT);

        // Volontairement incoherents entre eux (taux et montant ne correspondent pas) - doit quand meme
        // etre respecte tel quel, aucun recalcul.
        Object resolution = resolveCommission(fx.service(), 1L, partenaire, 500_000L, new BigDecimal("0.20"), 12_345L);

        assertEquals(0, new BigDecimal("0.20").compareTo(rateOf(resolution)));
        assertEquals(12_345L, amountOf(resolution));
        verify(fx.produitCommissionTauxRepository(), never()).findByProduitIdAndTypePartenaire(any(), any());
    }

    @Test
    void resolveCommission_aucunFourniAvecPartenaire_resoutDepuisLaTable() throws Exception {
        Fixture fx = newFixture();
        Partenaire partenaire = partenaire(1L, PartenaireType.AGENT);
        ProduitCommissionTaux taux = ProduitCommissionTaux.builder().id(9L)
                .typePartenaire(PartenaireType.AGENT).tauxCommission(new BigDecimal("10")).libTauxCommission("%").active(true).build();
        when(fx.produitCommissionTauxRepository().findByProduitIdAndTypePartenaire(1L, PartenaireType.AGENT))
                .thenReturn(Optional.of(taux));

        Object resolution = resolveCommission(fx.service(), 1L, partenaire, 500_000L, null, null);

        assertEquals(0, new BigDecimal("0.1000").compareTo(rateOf(resolution)));
        assertEquals(50_000L, amountOf(resolution));
    }

    @Test
    void resolveCommission_aucunFourniSansPartenaire_estRejeteAvec400() {
        Fixture fx = newFixture();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolveCommission(fx.service(), 1L, null, 500_000L, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    /**
     * Couvre toResponse()/safe() - correctif du 2026-09-08 (bug reel signale par l'utilisateur, "GET
     * /contracts et GET /contracts/{id} en 500") : un contrat dont la FK insured_id pointe vers une
     * ligne Insured supprimee (reference orpheline) ne doit plus faire planter la reponse - les champs
     * derives de l'assure doivent simplement etre nuls. Le proxy Hibernate leve EntityNotFoundException
     * uniquement quand on lit un champ AUTRE que l'id, d'ou le mock qui laisse getId() passer mais fait
     * echouer getPrenoms()/getNom()/getClientPhone()/getClientCity().
     */
    @Test
    void toResponse_assureAvecFkOrpheline_neLevePasEtRendDesChampsNuls() throws Exception {
        Fixture fx = newFixture();
        when(fx.contractRepository().findByContratPrecedentId(10L)).thenReturn(Optional.empty());

        Insured insuredOrphelin = mock(Insured.class);
        when(insuredOrphelin.getId()).thenReturn(3L);
        when(insuredOrphelin.getPrenoms()).thenThrow(new EntityNotFoundException("Insured 3 introuvable"));
        when(insuredOrphelin.getNom()).thenThrow(new EntityNotFoundException("Insured 3 introuvable"));
        when(insuredOrphelin.getClientPhone()).thenThrow(new EntityNotFoundException("Insured 3 introuvable"));
        when(insuredOrphelin.getClientCity()).thenThrow(new EntityNotFoundException("Insured 3 introuvable"));

        Contract contract = Contract.builder()
                .id(10L)
                .policyNumber("POL-ORPHAN")
                .insured(insuredOrphelin)
                .build();

        ContractResponse response = assertDoesNotThrow(() -> toResponse(fx.service(), contract));

        assertEquals(10L, response.id());
        assertEquals(3L, response.insuredId());
        assertNull(response.insuredName());
        assertNull(response.clientFirstName());
        assertNull(response.clientLastName());
        assertNull(response.clientPhone());
        assertNull(response.clientCity());
    }

    /**
     * Bug reel du 2026-09-14 : delete() ne nettoyait pas les rappels SMS (NotificateurSms) lies au
     * contrat avant de le supprimer, ce qui faisait echouer la suppression en
     * DataIntegrityViolationException (fk_notificateur_sms_contract, pas de cascade en base) - un 500
     * brut au lieu d'une suppression reussie. Verifie que le nettoyage est desormais fait AVANT la
     * suppression du contrat lui-meme.
     */
    @Test
    void delete_nettoieLesRappelsSmsDuContratAvantDeLeSupprimer() {
        Fixture fx = newFixture();
        when(fx.authService().currentPrincipal())
                .thenReturn(new ci.assurzen.security.AuthenticatedUserPrincipal(99L, "admin", "ADMIN"));
        Contract contract = Contract.builder().id(10L).policyNumber("CTR2026000010").build();
        when(fx.contractRepository().findById(10L)).thenReturn(Optional.of(contract));

        fx.service().delete(10L);

        verify(fx.smsNotificationService()).deleteRemindersForContract(10L);
        verify(fx.contractRepository()).delete(contract);
    }
}
