package ci.assurzen.services;

import ci.assurzen.entities.DisbursementStatus;
import ci.assurzen.entities.FundOutflowDisbursement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Seule implementation de DisbursementService dans ce projet aujourd'hui - voir la javadoc de
 * l'interface pour la limite honnete (pas de passerelle bancaire reelle). */
@Service
@Slf4j
public class ManualDisbursementService implements DisbursementService {

    @Override
    public FundOutflowDisbursement process(FundOutflowDisbursement disbursement) {
        log.info("Decaissement {} confirme manuellement (pas de passerelle de paiement externe configuree)", disbursement.getReference());
        disbursement.setStatus(DisbursementStatus.SUCCESS);
        return disbursement;
    }
}
