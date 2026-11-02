package ci.assurzen.services;

import ci.assurzen.entities.DisbursementMethod;
import ci.assurzen.entities.DisbursementStatus;
import ci.assurzen.entities.FundOutflowDisbursement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualDisbursementServiceTest {

    @Test
    void process_marqueToujoursSuccess() {
        ManualDisbursementService service = new ManualDisbursementService();
        FundOutflowDisbursement disbursement = FundOutflowDisbursement.builder()
                .reference("FOD2026000001").amount(100000L).currency("XOF")
                .method(DisbursementMethod.BANK_TRANSFER).status(DisbursementStatus.PROCESSING).build();

        FundOutflowDisbursement result = service.process(disbursement);

        assertEquals(DisbursementStatus.SUCCESS, result.getStatus());
    }
}
