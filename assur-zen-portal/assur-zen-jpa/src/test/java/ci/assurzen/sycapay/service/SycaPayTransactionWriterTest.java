package ci.assurzen.sycapay.service;

import ci.assurzen.enums.CodePrefix;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.repository.SycaPayTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Couvre la garde d'idempotence (section 10) : un contrat avec une transaction active/non reconciliee
 * ne doit jamais pouvoir en cumuler une seconde, tandis qu'un contrat FAILED doit pouvoir etre
 * retente (nouvelle reference, attempt_count incremente, MEME ligne reutilisee).
 */
class SycaPayTransactionWriterTest {

    private SycaPayTransactionWriter newWriter(SycaPayTransactionRepository repository) {
        CodeGeneratorService codeGeneratorService = mock(CodeGeneratorService.class);
        AtomicLong seq = new AtomicLong(1);
        when(codeGeneratorService.generate(CodePrefix.SYCAPAY_TRANSACTION))
                .thenAnswer(inv -> "SYC2026" + String.format("%06d", seq.getAndIncrement()));
        return new SycaPayTransactionWriter(repository, codeGeneratorService);
    }

    @Test
    void createOrReactivatePending_creeUneNouvelleLigneSiAucuneNexiste() {
        SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
        when(repository.findByContractId(10L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            SycaPayTransaction t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        SycaPayTransactionWriter writer = newWriter(repository);

        SycaPayTransaction created = writer.createOrReactivatePending(10L, 50L, 25000L, "XOF", "Jean Koffi", "+2250700000000");

        assertEquals(SycaPayTransactionStatus.PENDING, created.getStatus());
        assertEquals(1, created.getAttemptCount());
        assertEquals("SYC2026000001", created.getTransactionReference());
    }

    @Test
    void createOrReactivatePending_rejetteSiUneTransactionEstDejaEnCoursOuReussie() {
        for (SycaPayTransactionStatus statut : new SycaPayTransactionStatus[]{
                SycaPayTransactionStatus.PENDING, SycaPayTransactionStatus.PROCESSING,
                SycaPayTransactionStatus.SUCCESS, SycaPayTransactionStatus.UNKNOWN}) {
            SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
            SycaPayTransaction existing = new SycaPayTransaction();
            existing.setId(1L);
            existing.setStatus(statut);
            existing.setTransactionReference("SYC2026000001");
            when(repository.findByContractId(10L)).thenReturn(Optional.of(existing));
            SycaPayTransactionWriter writer = newWriter(repository);

            assertThrows(SycaPayDuplicatePaymentException.class,
                    () -> writer.createOrReactivatePending(10L, 50L, 25000L, "XOF", "Jean Koffi", "+2250700000000"),
                    "statut " + statut + " aurait du etre rejete");
        }
    }

    @Test
    void createOrReactivatePending_reactiveLaMemeLigneApresUnEchec() {
        SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
        SycaPayTransaction existing = new SycaPayTransaction();
        existing.setId(1L);
        existing.setStatus(SycaPayTransactionStatus.FAILED);
        existing.setTransactionReference("SYC2026000001");
        existing.setAttemptCount(1);
        when(repository.findByContractId(10L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SycaPayTransactionWriter writer = newWriter(repository);

        SycaPayTransaction reactivated = writer.createOrReactivatePending(10L, 50L, 25000L, "XOF", "Jean Koffi", "+2250700000000");

        assertEquals(1L, reactivated.getId());
        assertEquals(SycaPayTransactionStatus.PENDING, reactivated.getStatus());
        assertEquals(2, reactivated.getAttemptCount());
        assertEquals("SYC2026000001", reactivated.getTransactionReference(), "nouvelle reference generee, valeur due au stub sequentiel");
    }

    @Test
    void finalizeTransaction_refuseUneTransitionInvalide() {
        SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
        SycaPayTransaction existing = new SycaPayTransaction();
        existing.setId(1L);
        existing.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        SycaPayTransactionWriter writer = newWriter(repository);

        assertThrows(IllegalStateException.class, () -> writer.finalizeTransaction(
                1L, SycaPayTransactionStatus.FAILED, null, null, null, null, null, null));
    }

    @Test
    void finalizeTransaction_appliqueUneTransitionValide() {
        SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
        SycaPayTransaction existing = new SycaPayTransaction();
        existing.setId(1L);
        existing.setStatus(SycaPayTransactionStatus.PENDING);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SycaPayTransactionWriter writer = newWriter(repository);

        SycaPayTransaction updated = writer.finalizeTransaction(
                1L, SycaPayTransactionStatus.SUCCESS, "REFTRANSFER", null, 0, "SUCCESS", null, null);

        assertEquals(SycaPayTransactionStatus.SUCCESS, updated.getStatus());
        assertEquals("REFTRANSFER", updated.getSycapayReference());
    }

    @AfterEach
    void viderLeContexteDeSecurite() {
        SecurityContextHolder.clearContext();
    }

    private static void connecterEnTantQue(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(1L, username, "ADMIN"), null, List.of()));
    }

    private static SycaPayTransactionRepository repositoryAvec(SycaPayTransaction existing) {
        SycaPayTransactionRepository repository = mock(SycaPayTransactionRepository.class);
        when(repository.findByContractId(10L)).thenReturn(Optional.ofNullable(existing));
        if (existing != null) {
            when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        }
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return repository;
    }

    private static SycaPayTransaction transactionEnEchecAvecIdentifiantsSycaPay() {
        SycaPayTransaction failed = new SycaPayTransaction();
        failed.setId(1L);
        failed.setStatus(SycaPayTransactionStatus.FAILED);
        failed.setTransactionReference("SYC2026000001");
        failed.setAttemptCount(1);
        failed.setSycapayReference("REF-ANCIEN-VIREMENT");
        failed.setSycapayTransactionId("TX-ANCIEN-VIREMENT");
        return failed;
    }

    /** Correctif 2026-09-23 (#1) : une nouvelle tentative ne doit jamais heriter des identifiants SycaPay
     * de l'ancien virement. */
    @Test
    void reactivation_effaceLesIdentifiantsSycaPayDeLaTentativePrecedente() {
        SycaPayTransaction failed = transactionEnEchecAvecIdentifiantsSycaPay();
        SycaPayTransactionWriter writer = newWriter(repositoryAvec(failed));

        SycaPayTransaction retried = writer.createOrReactivatePending(10L, 50L, 25000L, "XOF", "Jean Koffi", "+2250700000000");

        assertNull(retried.getSycapayReference());
        assertNull(retried.getSycapayTransactionId());
    }

    @Test
    void reactivationPuisTimeout_neRessusciteJamaisLAncienneReferenceSycaPay() {
        SycaPayTransaction failed = transactionEnEchecAvecIdentifiantsSycaPay();
        SycaPayTransactionWriter writer = newWriter(repositoryAvec(failed));

        writer.createOrReactivatePending(10L, 50L, 25000L, "XOF", "Jean Koffi", "+2250700000000");
        SycaPayTransaction unknown = writer.finalizeTransaction(1L, SycaPayTransactionStatus.UNKNOWN,
                null, null, null, null, null, "Timeout");

        assertNull(unknown.getSycapayReference());
        assertNull(unknown.getSycapayTransactionId());
    }

    /** Correctif 2026-09-23 (#2) : created_by/updated_by renseignes avec l'utilisateur connecte. */
    @Test
    void creation_renseigneCreatedByEtUpdatedByAvecLUtilisateurConnecte() {
        connecterEnTantQue("admin.sycapay");
        SycaPayTransactionWriter writer = newWriter(repositoryAvec(null));

        SycaPayTransaction created = writer.createOrReactivatePending(10L, 50L, 25000L, "XOF", "Jean Koffi", "+2250700000000");

        assertEquals("admin.sycapay", created.getCreatedBy());
        assertEquals("admin.sycapay", created.getUpdatedBy());
    }

    @Test
    void finalisation_horsRequeteAuthentifiee_utiliseSystem() {
        SycaPayTransaction pending = new SycaPayTransaction();
        pending.setId(1L);
        pending.setStatus(SycaPayTransactionStatus.PENDING);
        pending.setCreatedBy("admin.sycapay");
        SycaPayTransactionWriter writer = newWriter(repositoryAvec(pending));

        SycaPayTransaction done = writer.finalizeTransaction(1L, SycaPayTransactionStatus.SUCCESS, "REF", null, 0, "OK", null, null);

        assertEquals(SycaPayTransactionWriter.SYSTEM_USER, done.getUpdatedBy());
        assertEquals("admin.sycapay", done.getCreatedBy(), "created_by ne doit jamais etre ecrase");
    }

    /** Correctif 2026-09-23 (#4) : processing_date = date du PREMIER envoi, jamais decalee. */
    @Test
    void markTransferSent_poseLaDateUneSeuleFois_etProcessingNeLaDecalePas() {
        SycaPayTransaction pending = new SycaPayTransaction();
        pending.setId(1L);
        pending.setStatus(SycaPayTransactionStatus.PENDING);
        SycaPayTransactionWriter writer = newWriter(repositoryAvec(pending));

        Instant premierEnvoi = writer.markTransferSent(1L).getProcessingDate();
        assertNotNull(premierEnvoi);
        assertEquals(premierEnvoi, writer.markTransferSent(1L).getProcessingDate(), "2e envoi (retry token) : date conservee");

        SycaPayTransaction processing = writer.finalizeTransaction(1L, SycaPayTransactionStatus.PROCESSING, "REF", null, 0, "pending", null, null);
        assertEquals(premierEnvoi, processing.getProcessingDate());
    }

    /** Correctif 2026-09-23 (#3) : identifiant SycaPay renseigne apres coup, jamais ecrase. */
    @Test
    void recordSycapayTransactionId_renseigneSansEcraserNiAccepterDeValeurVide() {
        SycaPayTransaction success = new SycaPayTransaction();
        success.setId(1L);
        success.setStatus(SycaPayTransactionStatus.SUCCESS);
        SycaPayTransactionRepository repository = repositoryAvec(success);
        SycaPayTransactionWriter writer = newWriter(repository);

        writer.recordSycapayTransactionId(1L, "  ");
        assertNull(success.getSycapayTransactionId());

        writer.recordSycapayTransactionId(1L, "TXWAVE-1");
        writer.recordSycapayTransactionId(1L, "TXWAVE-2");
        assertEquals("TXWAVE-1", success.getSycapayTransactionId());
        assertEquals(SycaPayTransactionStatus.SUCCESS, success.getStatus());
    }

    /** Paiement des commissions partenaire (2026-09-23) : beneficiaire PARTENAIRE, sans commercial. */
    @Test
    void creation_pourUnPartenaire_renseigneLeTypeEtLePartenaire() {
        SycaPayTransactionWriter writer = newWriter(repositoryAvec(null));

        SycaPayTransaction created = writer.createOrReactivatePending(10L, SycaPayBeneficiaryType.PARTENAIRE, null, 7L,
                12000L, "XOF", "Ama Koffi", "0757000007");

        assertEquals(SycaPayBeneficiaryType.PARTENAIRE, created.getBeneficiaryType());
        assertEquals(7L, created.getPartenaireId());
        assertNull(created.getCommercialId());
    }

    @Test
    void transactionAnterieure_sansType_estCommerciale() {
        SycaPayTransaction ancienne = new SycaPayTransaction();
        ancienne.setBeneficiaryType(null);
        assertEquals(SycaPayBeneficiaryType.COMMERCIAL, ancienne.getBeneficiaryType());
    }
}
