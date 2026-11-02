package ci.assurzen.services;

import ci.assurzen.entities.ContractStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Un contrat signe a toujours une date de signature (2026-10-04). */
class ContractsServiceSignedAtTest {

    private static final LocalDate FOURNIE = LocalDate.of(2026, 9, 12);
    private static final LocalDate EXISTANTE = LocalDate.of(2026, 9, 1);

    @Test
    void signe_dateFournie_conservee() {
        assertEquals(FOURNIE, ContractsService.signedAtEffectif(ContractStatus.signed, FOURNIE, EXISTANTE));
    }

    @Test
    void signe_sansDate_garderLExistante_sinonAujourdhui() {
        assertEquals(EXISTANTE, ContractsService.signedAtEffectif(ContractStatus.signed, null, EXISTANTE));
        assertEquals(LocalDate.now(), ContractsService.signedAtEffectif(ContractStatus.signed, null, null));
    }

    @Test
    void nonSigne_comportementInchange() {
        assertNull(ContractsService.signedAtEffectif(ContractStatus.draft, null, EXISTANTE));
        assertEquals(FOURNIE, ContractsService.signedAtEffectif(ContractStatus.pending, FOURNIE, null));
    }
}
