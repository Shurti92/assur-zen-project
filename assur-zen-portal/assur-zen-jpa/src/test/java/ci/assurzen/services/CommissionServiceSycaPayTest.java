package ci.assurzen.services;

import ci.assurzen.dto.CommissionPaymentRequest;
import ci.assurzen.dto.CommissionPaymentResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.entities.CommissionPartialPaymentRequest;
import ci.assurzen.entities.CommissionPayment;
import ci.assurzen.entities.CommissionStatus;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartialPaymentRequestStatus;
import ci.assurzen.entities.User;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.CommissionPartialPaymentRequestRepository;
import ci.assurzen.repositories.CommissionPaymentRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.PartenaireRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import ci.assurzen.sycapay.config.SycaPayProperties;
import ci.assurzen.sycapay.dto.SycaPayPaymentCommand;
import ci.assurzen.sycapay.dto.SycaPayPaymentOutcome;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.exception.SycaPayException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import ci.assurzen.sycapay.service.SycaPayPaymentService;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Commissions partenaire et SycaPay (2026-09-23) : protection du paiement MANUEL contre un double
 * paiement, enregistrement d'un paiement AUTOMATIQUE, et champs SycaPay de l'historique.
 */
class CommissionServiceSycaPayTest {

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final CommissionPaymentRepository paymentRepository = mock(CommissionPaymentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final SycaPayTransactionRepository sycaPayTransactionRepository = mock(SycaPayTransactionRepository.class);
    private final SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
    private final SycaPayProperties sycaPayProperties = mock(SycaPayProperties.class);
    private final SycaPayPaymentService sycaPayPaymentService = mock(SycaPayPaymentService.class);
    private final CommissionPartialPaymentRequestRepository demandesPartielles = mock(CommissionPartialPaymentRequestRepository.class);
    private final CommissionService service = new CommissionService(contractRepository, mock(PartenaireRepository.class),
            paymentRepository, demandesPartielles, userRepository, authService,
            mock(BusinessMetrics.class), ledgerService, smsNotificationService,
            mock(TelegramNotificationService.class), sycaPayTransactionRepository, sycaPayProperties, sycaPayPaymentService,
            mock(PlatformTransactionManager.class));

    private Contract contratSigne(CommissionStatus commissionStatus) {
        Partenaire partenaire = Partenaire.builder().id(7L).nom("Koffi").prenoms("Ama").telephone("0700000007").build();
        Contract contract = Contract.builder().id(30L).policyNumber("POL-123").partenaire(partenaire)
                .status(ContractStatus.signed).partnerShare(12000L).commissionStatus(commissionStatus)
                .createdAt(Instant.now()).build();
        when(contractRepository.findById(30L)).thenReturn(Optional.of(contract));
        return contract;
    }

    private void adminConnecte() {
        when(authService.currentPrincipal()).thenReturn(new AuthenticatedUserPrincipal(1L, "admin", "ADMIN"));
        User admin = new User();
        admin.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    @Test
    void paiementManuel_virementSycaPayActifOuReussi_refuseAvecMessageCitantLeContrat() {
        adminConnecte();
        Contract contract = contratSigne(CommissionStatus.pending);
        when(sycaPayTransactionRepository.existsByContractIdAndStatusIn(eq(30L), any())).thenReturn(true);

        SycaPayDuplicatePaymentException ex = assertThrows(SycaPayDuplicatePaymentException.class,
                () -> service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")));

        assertEquals("Un paiement SycaPay est deja en cours ou effectue pour le contrat POL-123.", ex.getMessage());
        assertEquals(CommissionStatus.pending, contract.getCommissionStatus());
        verify(paymentRepository, never()).save(any());
    }

    /** Seul un echec (FAILED) ou l'absence de transaction laisse la commission payable a la main. */
    @SuppressWarnings("unchecked")
    @Test
    void paiementManuel_statutsBloquants_sontPendingProcessingUnknownSuccess() {
        adminConnecte();
        contratSigne(CommissionStatus.pending);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        ArgumentCaptor<Collection<SycaPayTransactionStatus>> statuts = ArgumentCaptor.forClass(Collection.class);
        verify(sycaPayTransactionRepository).existsByContractIdAndStatusIn(eq(30L), statuts.capture());
        assertEquals(java.util.EnumSet.of(SycaPayTransactionStatus.PENDING, SycaPayTransactionStatus.PROCESSING,
                SycaPayTransactionStatus.UNKNOWN, SycaPayTransactionStatus.SUCCESS), java.util.EnumSet.copyOf(statuts.getValue()));
        verify(paymentRepository).save(any());
    }

    @Test
    void paiementAutomatique_marqueLeContratPaye_sansAdmin_etRattacheLaTransaction() {
        Contract contract = contratSigne(CommissionStatus.pending);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(55L);
        transaction.setTransactionReference("SYC2026000055");
        transaction.setAmount(12000L);

        CommissionPayment payment = service.enregistrerPaiementAutomatique(30L, transaction);

        assertEquals(CommissionStatus.paid, contract.getCommissionStatus());
        assertTrue(payment.isAutomatic());
        assertNull(payment.getPaidBy());
        assertEquals(55L, payment.getSycapayTransactionId());
        assertEquals("SYC2026000055", payment.getReference());
        verify(ledgerService).enregistrerCommission(contract, 12000L);
    }

    @Test
    void paiementAutomatique_commissionDejaPayee_nEnregistreRien() {
        contratSigne(CommissionStatus.paid);

        assertNull(service.enregistrerPaiementAutomatique(30L, new SycaPayTransaction()));
        verify(paymentRepository, never()).save(any());
        verify(ledgerService, never()).enregistrerCommission(any(), anyLong());
    }

    @Test
    void historique_exposeLesChampsSycaPayEtLIndicateurAutomatique() {
        Contract contract = contratSigne(CommissionStatus.paid);
        CommissionPayment automatique = CommissionPayment.builder().id(1L).contract(contract).partenaire(contract.getPartenaire())
                .amount(12000L).automatic(true).sycapayTransactionId(55L).reference("SYC2026000055").build();
        CommissionPayment manuel = CommissionPayment.builder().id(2L).contract(contract).partenaire(contract.getPartenaire())
                .amount(5000L).reference("LOT-1").build();
        when(paymentRepository.findByPartenaireId(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(automatique, manuel)));
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(55L);
        transaction.setTransactionReference("SYC2026000055");
        transaction.setStatus(SycaPayTransactionStatus.SUCCESS);
        transaction.setSycapayResponseMessage("DEPOT EFFECTUE AVEC SUCCESS");
        when(sycaPayTransactionRepository.findAllById(List.of(55L))).thenReturn(List.of(transaction));

        PageResponse<CommissionPaymentResponse> page = service.getPaymentHistory(7L, 0, 20);

        CommissionPaymentResponse auto = page.content().get(0);
        assertTrue(auto.automatic());
        assertEquals("SUCCESS", auto.sycapayStatus());
        assertEquals("SYC2026000055", auto.sycapayTransactionReference());
        assertEquals(55L, auto.sycapayTransactionPk());
        assertEquals("DEPOT EFFECTUE AVEC SUCCESS", auto.sycapayMessage());
        CommissionPaymentResponse man = page.content().get(1);
        assertFalse(man.automatic());
        assertNull(man.sycapayStatus());
        assertNull(man.sycapayTransactionPk());
    }

    /**
     * Regression reelle du 2026-09-23 (GET /commissions/payments en 500) : paiement dont le partenaire a
     * ete supprime. Le proxy Hibernate leve EntityNotFoundException des qu'on le lit - meme sur getId()
     * avec Hibernate 7. L'historique doit rester affichable, avec l'id conserve (lu dans le proxy).
     */
    @Test
    void historique_partenaireSupprime_restAffiche_avecSonId() {
        Partenaire supprime = mock(Partenaire.class, org.mockito.Mockito.withSettings().extraInterfaces(HibernateProxy.class));
        LazyInitializer initializer = mock(LazyInitializer.class);
        when(((HibernateProxy) supprime).getHibernateLazyInitializer()).thenReturn(initializer);
        when(initializer.getInternalIdentifier()).thenReturn(3L);
        when(supprime.getId()).thenThrow(new EntityNotFoundException("Partenaire 3"));
        when(supprime.getNom()).thenThrow(new EntityNotFoundException("Partenaire 3"));
        Contract contract = contratSigne(CommissionStatus.paid);
        CommissionPayment orphelin = CommissionPayment.builder().id(1L).contract(contract).partenaire(supprime)
                .amount(300L).reference("Virement bancaire juillet 2026").build();
        when(paymentRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orphelin)));

        PageResponse<CommissionPaymentResponse> page = service.getPaymentHistory(null, 0, 20);

        CommissionPaymentResponse ligne = page.content().get(0);
        assertEquals(3L, ligne.partenaireId());
        assertEquals(CommissionService.PARTENAIRE_SUPPRIME, ligne.partenaireNom());
        assertEquals(300L, ligne.amount());
    }

    /** Mode test : le virement porte le montant de test (100) mais la commission est enregistree a sa
     * valeur reelle (partnerShare), en paiement comme en comptabilite. */
    @Test
    void paiementAutomatique_enModeTest_enregistreLeMontantReelDeLaCommission() {
        Contract contract = contratSigne(CommissionStatus.pending);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SycaPayTransaction virementTest = new SycaPayTransaction();
        virementTest.setId(56L);
        virementTest.setTransactionReference("SYC2026000056");
        virementTest.setAmount(100L);
        virementTest.setTestMode(true);

        CommissionPayment payment = service.enregistrerPaiementAutomatique(30L, virementTest);

        assertEquals(12000L, payment.getAmount());
        verify(ledgerService).enregistrerCommission(contract, 12000L);
    }

    /** Statut du SMS de confirmation (2026-09-24) : enregistre sur le paiement a l'envoi, relu par l'historique. */
    @Test
    void paiementManuel_resultatDuSms_conserveSurLePaiement_etExposeParLHistorique() {
        adminConnecte();
        Contract contract = contratSigne(CommissionStatus.pending);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionPayment>> saved = ArgumentCaptor.forClass(List.class);
        verify(paymentRepository).saveAll(saved.capture());
        // SmsNotificationService mocke : sendFromTemplate renvoie false -> echec trace (pas null).
        assertEquals(Boolean.FALSE, saved.getValue().get(0).getNotificationEnvoyee());

        CommissionPayment enregistre = CommissionPayment.builder().id(9L).contract(contract).partenaire(contract.getPartenaire())
                .amount(12000L).notificationEnvoyee(true).build();
        when(paymentRepository.findByPartenaireId(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(enregistre)));
        assertEquals(Boolean.TRUE, service.getPaymentHistory(7L, 0, 20).content().get(0).notificationEnvoyee());
    }

    @Test
    void historique_paiementAncienSansTrace_notificationNull() {
        Contract contract = contratSigne(CommissionStatus.paid);
        CommissionPayment ancien = CommissionPayment.builder().id(9L).contract(contract).partenaire(contract.getPartenaire())
                .amount(12000L).build();
        when(paymentRepository.findByPartenaireId(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ancien)));

        assertNull(service.getPaymentHistory(7L, 0, 20).content().get(0).notificationEnvoyee());
    }

    // ---- Paiement manuel par virement SycaPay (hybride, decision du 2026-09-26) ----

    private Contract contrat(long id, Partenaire partenaire, long montant) {
        Contract contract = Contract.builder().id(id).policyNumber("POL-" + id).partenaire(partenaire)
                .status(ContractStatus.signed).partnerShare(montant).commissionStatus(CommissionStatus.pending)
                .createdAt(Instant.now()).build();
        when(contractRepository.findById(id)).thenReturn(Optional.of(contract));
        return contract;
    }

    private Partenaire partenaireAvecWave() {
        return Partenaire.builder().id(7L).nom("Koffi").prenoms("Ama").telephone("0700000007").numeroWaveCommission("0707070707").build();
    }

    private void virementActif() {
        adminConnecte();
        when(sycaPayProperties.isEnabled()).thenReturn(true);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void virement_succes_enregistreLePaiementManuelLieALaTransaction() {
        virementActif();
        Contract contract = contrat(30L, partenaireAvecWave(), 12000L);
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 55L, "SYC2026000055", "DEPOT EFFECTUE"));
        when(smsNotificationService.sendFromTemplate(any(), eq("COMMISSION_PAYEE"), any(), any())).thenReturn(true);

        List<CommissionPaymentResponse> reponses = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        ArgumentCaptor<SycaPayPaymentCommand> commande = ArgumentCaptor.forClass(SycaPayPaymentCommand.class);
        verify(sycaPayPaymentService).payCommission(commande.capture());
        assertEquals(12000L, commande.getValue().amount());
        assertEquals("0707070707", commande.getValue().beneficiaryPhone());
        assertEquals(7L, commande.getValue().partenaireId());
        assertEquals(CommissionStatus.paid, contract.getCommissionStatus());
        ArgumentCaptor<CommissionPayment> paiement = ArgumentCaptor.forClass(CommissionPayment.class);
        verify(paymentRepository).save(paiement.capture());
        assertEquals(55L, paiement.getValue().getSycapayTransactionId());
        assertFalse(paiement.getValue().isAutomatic());
        assertEquals(1L, paiement.getValue().getPaidBy().getId());
        CommissionPaymentResponse reponse = reponses.get(0);
        assertEquals("SUCCESS", reponse.sycapayStatus());
        assertEquals(55L, reponse.sycapayTransactionPk());
        assertEquals("VIREMENT_SYCAPAY", reponse.modePaiement());
        assertEquals(Boolean.TRUE, reponse.notificationEnvoyee());
    }

    @Test
    void virement_echec_rienNestEnregistre_commissionResteAPayer() {
        virementActif();
        Contract contract = contrat(30L, partenaireAvecWave(), 12000L);
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.FAILED, 56L, "SYC2026000056", "Solde insuffisant"));

        CommissionPaymentResponse reponse = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")).get(0);

        assertEquals(CommissionStatus.pending, contract.getCommissionStatus());
        verify(paymentRepository, never()).save(any());
        verify(smsNotificationService, never()).sendFromTemplate(any(), any(), any(), any());
        assertEquals("FAILED", reponse.sycapayStatus());
        assertNull(reponse.id());
        assertNull(reponse.paidAt());
        assertEquals(12000L, reponse.amount());
        assertEquals("Solde insuffisant", reponse.sycapayMessage());
    }

    @Test
    void virement_chaqueContratTraiteIndependamment() {
        virementActif();
        Partenaire partenaire = partenaireAvecWave();
        Contract reussi = contrat(30L, partenaire, 12000L);
        Contract echoue = contrat(31L, partenaire, 8000L);
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 55L, "SYC2026000055", "OK"),
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.UNKNOWN, 57L, "SYC2026000057", "Timeout"));

        List<CommissionPaymentResponse> reponses = service.pay(new CommissionPaymentRequest(List.of(30L, 31L), "LOT-1"));

        assertEquals(CommissionStatus.paid, reussi.getCommissionStatus());
        assertEquals(CommissionStatus.pending, echoue.getCommissionStatus());
        assertEquals(List.of("SUCCESS", "UNKNOWN"), reponses.stream().map(CommissionPaymentResponse::sycapayStatus).toList());
    }

    @Test
    void virement_partenaireSansNumeroWave_rejeteSansAppelSycaPay() {
        virementActif();
        contrat(30L, Partenaire.builder().id(7L).nom("Koffi").prenoms("Ama").build(), 12000L);

        CommissionPaymentResponse reponse = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")).get(0);

        assertEquals("REJECTED", reponse.sycapayStatus());
        assertTrue(reponse.sycapayMessage().startsWith("Numero Wave de commission non renseigne pour le partenaire Ama Koffi"));
        verify(sycaPayPaymentService, never()).payCommission(any());
    }

    @Test
    void virement_serviceIndisponible_rejeteSansDetailTechnique() {
        virementActif();
        contrat(30L, partenaireAvecWave(), 12000L);
        when(sycaPayPaymentService.payCommission(any())).thenThrow(new SycaPayException("merchant-id/api-key/secret-key incomplets"));

        CommissionPaymentResponse reponse = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")).get(0);

        assertEquals("REJECTED", reponse.sycapayStatus());
        assertEquals("Service de paiement SycaPay indisponible - aucun virement n'est parti", reponse.sycapayMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void sycapayDesactive_enregistrementHorsApplicationCommeAvant_sansVirement() {
        adminConnecte();
        when(sycaPayProperties.isEnabled()).thenReturn(false);
        Contract contract = contrat(30L, partenaireAvecWave(), 12000L);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommissionPaymentResponse reponse = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")).get(0);

        verify(sycaPayPaymentService, never()).payCommission(any());
        assertEquals(CommissionStatus.paid, contract.getCommissionStatus());
        assertEquals("ENREGISTREMENT_MANUEL", reponse.modePaiement());
        assertNull(reponse.sycapayStatus());
    }

    @Test
    void plusDeSmsDemandeRecueAvantTraitement() {
        adminConnecte();
        contrat(30L, partenaireAvecWave(), 12000L);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        verify(smsNotificationService, never()).sendFromTemplate(any(), eq("COMMISSION_DEMANDE_RECUE"), any(), any());
        verify(smsNotificationService).sendFromTemplate(any(), eq("COMMISSION_PAYEE"), any(), any());
    }

    // ---- Imputation des avances (demandes de paiement partiel payees), 2026-09-26 ----

    private CommissionPartialPaymentRequest avance(long id, long montant, Long dejaImpute) {
        return avance(id, montant, dejaImpute, null);
    }

    private CommissionPartialPaymentRequest avance(long id, long montant, Long dejaImpute, Boolean comptabilisee) {
        CommissionPartialPaymentRequest demande = CommissionPartialPaymentRequest.builder().id(id).requestedAmount(montant)
                .comptabilisee(comptabilisee)
                .status(PartialPaymentRequestStatus.paid).montantImpute(dejaImpute)
                .requestedAt(Instant.parse("2026-09-26T02:51:0" + id + "Z")).build();
        when(demandesPartielles.findById(id)).thenReturn(Optional.of(demande));
        return demande;
    }

    private void avancesDuPartenaire7(CommissionPartialPaymentRequest... demandes) {
        when(demandesPartielles.findByPartenaireIdAndStatusOrderByRequestedAtAsc(7L, PartialPaymentRequestStatus.paid))
                .thenReturn(List.of(demandes));
    }

    /** Cas reel du partenaire 9 en prod (2026-09-26) : 30 000 a payer, 19 200 deja avances -> 10 800 verses. */
    @Test
    void horsApplication_avancesDeduites_resteEnregistre_toutesLesAvancesConsommees() {
        adminConnecte();
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Partenaire partenaire = partenaireAvecWave();
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 0; i < 6; i++) {
            contrat(100 + i, partenaire, 1500L);
            ids.add(100 + i);
        }
        for (long i = 0; i < 21; i++) {
            contrat(200 + i, partenaire, 1000L);
            ids.add(200 + i);
        }
        CommissionPartialPaymentRequest premiere = avance(1L, 12000L, null);
        CommissionPartialPaymentRequest seconde = avance(2L, 7200L, null);
        avancesDuPartenaire7(premiere, seconde);

        List<CommissionPaymentResponse> reponses = service.pay(new CommissionPaymentRequest(ids, "SOLDE-9"));

        assertEquals(27, reponses.size());
        assertEquals(30000L, reponses.stream().mapToLong(CommissionPaymentResponse::amount).sum());
        assertEquals(19200L, reponses.stream().mapToLong(r -> r.avanceImputee() == null ? 0 : r.avanceImputee()).sum());
        assertEquals(10800L, reponses.stream().mapToLong(CommissionPaymentResponse::montantVerse).sum());
        assertEquals(12000L, premiere.getMontantImpute());
        assertEquals(7200L, seconde.getMontantImpute());
        assertEquals(0L, premiere.avanceRestante() + seconde.avanceRestante());
        verify(smsNotificationService).sendFromTemplate(any(), eq("COMMISSION_PAYEE"),
                eq(java.util.Map.of("montant", "10 800", "nombreContrats", "27", "reference", "SOLDE-9")), any());
    }

    @Test
    void contratEntierementCouvertParLAvance_aucunVirement_modeAvanceImputee() {
        virementActif();
        contrat(30L, partenaireAvecWave(), 5000L);
        CommissionPartialPaymentRequest avance = avance(1L, 8000L, null);
        avancesDuPartenaire7(avance);

        CommissionPaymentResponse reponse = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")).get(0);

        verify(sycaPayPaymentService, never()).payCommission(any());
        assertEquals("AVANCE_IMPUTEE", reponse.modePaiement());
        assertEquals(5000L, reponse.avanceImputee());
        assertEquals(0L, reponse.montantVerse());
        assertEquals(5000L, avance.getMontantImpute());
        assertEquals(3000L, avance.avanceRestante());
        verify(smsNotificationService, never()).sendFromTemplate(any(), any(), any(), any());
    }

    @Test
    void virement_seulLeResteEstVire() {
        virementActif();
        contrat(30L, partenaireAvecWave(), 12000L);
        avancesDuPartenaire7(avance(1L, 10000L, 7000L)); // 3 000 d'avance restante
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.SUCCESS, 55L, "SYC2026000055", "OK"));

        CommissionPaymentResponse reponse = service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")).get(0);

        ArgumentCaptor<SycaPayPaymentCommand> commande = ArgumentCaptor.forClass(SycaPayPaymentCommand.class);
        verify(sycaPayPaymentService).payCommission(commande.capture());
        assertEquals(9000L, commande.getValue().amount());
        assertEquals(3000L, reponse.avanceImputee());
        assertEquals(9000L, reponse.montantVerse());
        assertEquals("VIREMENT_SYCAPAY", reponse.modePaiement());
    }

    @Test
    void virementEchoue_lAvanceNestPasConsommee() {
        virementActif();
        contrat(30L, partenaireAvecWave(), 12000L);
        CommissionPartialPaymentRequest avance = avance(1L, 3000L, null);
        avancesDuPartenaire7(avance);
        when(sycaPayPaymentService.payCommission(any())).thenReturn(
                SycaPayPaymentOutcome.of(SycaPayTransactionStatus.FAILED, 56L, "SYC2026000056", "Solde insuffisant"));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        assertNull(avance.getMontantImpute());
        assertEquals(3000L, avance.avanceRestante());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void demandePartielleEnAttente_bloqueAvecUnMessageExact() {
        adminConnecte();
        contrat(30L, partenaireAvecWave(), 12000L);
        when(demandesPartielles.findByPartenaireIdAndStatusIn(eq(7L), any()))
                .thenReturn(List.of(CommissionPartialPaymentRequest.builder().id(3L).status(PartialPaymentRequestStatus.failed).build()));

        org.springframework.web.server.ResponseStatusException ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1")));

        assertTrue(ex.getReason().startsWith("Ce partenaire a une demande de paiement partiel en attente, en cours de virement ou en echec"));
    }

    @Test
    void paiementAutomatique_bloqueTantQuUneAvanceNestPasImputee() {
        avancesDuPartenaire7(avance(1L, 5000L, 5000L));
        assertFalse(service.aDesPaiementsPartielsEnCours(7L)); // avance entierement imputee
        avancesDuPartenaire7(avance(2L, 5000L, 2000L));
        assertTrue(service.aDesPaiementsPartielsEnCours(7L));  // 3 000 encore a imputer
    }

    // ---- Grand livre : pas de double comptage des avances (2026-09-26) ----

    @Test
    void avanceDejaComptabilisee_seulLeResteEstEcritAuGrandLivre() {
        adminConnecte();
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Contract contract = contrat(30L, partenaireAvecWave(), 12000L);
        avancesDuPartenaire7(avance(1L, 3000L, null, true));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        verify(ledgerService).enregistrerCommission(contract, 9000L);
    }

    @Test
    void avanceAnterieureJamaisComptabilisee_commissionEntiereEcriteAvecLeContrat() {
        adminConnecte();
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Contract contract = contrat(30L, partenaireAvecWave(), 12000L);
        avancesDuPartenaire7(avance(1L, 3000L, null, null));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        verify(ledgerService).enregistrerCommission(contract, 12000L);
    }

    @Test
    void contratEntierementCouvertParUneAvanceComptabilisee_aucuneNouvelleEcriture() {
        adminConnecte();
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        contrat(30L, partenaireAvecWave(), 5000L);
        avancesDuPartenaire7(avance(1L, 8000L, null, true));

        service.pay(new CommissionPaymentRequest(List.of(30L), "LOT-1"));

        verify(ledgerService, never()).enregistrerCommission(any(), anyLong());
    }
}
