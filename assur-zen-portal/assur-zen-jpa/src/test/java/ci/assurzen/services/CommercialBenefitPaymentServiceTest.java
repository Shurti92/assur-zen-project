package ci.assurzen.services;

import ci.assurzen.dto.CommercialBenefitStatus;
import ci.assurzen.entities.CommercialBenefitPayment;
import ci.assurzen.entities.StatutAvantageCommercial;
import ci.assurzen.entities.TypeAvantageCommercial;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Regles de statut des avantages (transport / communication) - 2026-11-01. */
class CommercialBenefitPaymentServiceTest {

    private final CommercialBenefitPaymentService service = new CommercialBenefitPaymentService(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    private SycaPayTransaction transaction(SycaPayTransactionStatus statut) {
        SycaPayTransaction t = new SycaPayTransaction();
        t.setId(5L);
        t.setStatus(statut);
        t.setTransactionReference("SYC1");
        return t;
    }

    private CommercialBenefitStatus statut(BigDecimal montant, CommercialBenefitPayment paiement, SycaPayTransaction t) {
        return service.statut(TypeAvantageCommercial.TRANSPORT, montant, paiement, t);
    }

    @Test
    void montantNulOuAvantageDesactive_estNonApplicable() {
        CommercialBenefitStatus s = statut(BigDecimal.ZERO, null, null);
        assertEquals(StatutAvantageCommercial.NON_APPLICABLE, s.statut());
        assertFalse(s.payable());
    }

    @Test
    void montantSansTentative_estAPayer() {
        CommercialBenefitStatus s = statut(BigDecimal.valueOf(15000), null, null);
        assertEquals(StatutAvantageCommercial.A_PAYER, s.statut());
        assertTrue(s.payable());
    }

    @Test
    void virementEnCours_estEnCoursEtNonRelancable() {
        for (SycaPayTransactionStatus st : new SycaPayTransactionStatus[]{SycaPayTransactionStatus.PENDING,
                SycaPayTransactionStatus.PROCESSING, SycaPayTransactionStatus.UNKNOWN}) {
            CommercialBenefitStatus s = statut(BigDecimal.valueOf(15000), null, transaction(st));
            assertEquals(StatutAvantageCommercial.EN_COURS, s.statut());
            assertFalse(s.payable());
        }
    }

    @Test
    void virementEchoue_estEchecEtRelancable() {
        CommercialBenefitStatus s = statut(BigDecimal.valueOf(15000), null, transaction(SycaPayTransactionStatus.FAILED));
        assertEquals(StatutAvantageCommercial.ECHEC, s.statut());
        assertTrue(s.payable());
    }

    @Test
    void avantagePaye_gardeLeMontantFigeEtNEstPlusPayable() {
        CommercialBenefitPayment p = new CommercialBenefitPayment();
        p.setAmount(BigDecimal.valueOf(10000));
        p.setPaidAt(Instant.now());
        CommercialBenefitStatus s = statut(BigDecimal.valueOf(99999), p, null);
        assertEquals(StatutAvantageCommercial.PAYE, s.statut());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(s.amount()));
        assertFalse(s.payable());
    }
}
