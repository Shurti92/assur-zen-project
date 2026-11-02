package ci.assurzen.services;

import ci.assurzen.entities.FundOutflowDisbursement;

/**
 * Abstraction du decaissement reel (§25). DEPENDANCE MANQUANTE signalee comme demande au §78 :
 * aucune passerelle de paiement externe (bancaire/mobile money) n'existe dans ce projet aujourd'hui
 * - CommissionPayment et CommissionPartialPaymentRequest sont deja, en production, des
 * enregistrements confirmes manuellement par un admin, jamais un appel API sortant.
 * ManualDisbursementService est la seule implementation : elle marque le decaissement PENDING->SUCCESS
 * de facon synchrone (confirmation manuelle que le virement/transfert a ete effectue hors-systeme),
 * pas une vraie integration bancaire.
 */
public interface DisbursementService {

    FundOutflowDisbursement process(FundOutflowDisbursement disbursement);
}
