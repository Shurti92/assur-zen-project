package ci.assurzen.services;

import ci.assurzen.dto.ContractRequest;
import ci.assurzen.dto.ContractResponse;
import ci.assurzen.dto.ContractSubscriptionRequest;
import ci.assurzen.dto.InsuredRegistrationRequest;
import ci.assurzen.dto.InsuredRegistrationResponse;
import ci.assurzen.dto.InsuredResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsuredRegistrationService {

    private final InsuredService insuredService;
    private final ContractsService contractsService;
    private final SmsNotificationService smsNotificationService;

    @Transactional
    public InsuredRegistrationResponse register(InsuredRegistrationRequest request) {
        Long insuredId = request.insured().insuredId();
        InsuredResponse insured = insuredId != null
                ? insuredService.update(insuredId, request.insured())
                : insuredService.create(request.insured());

        List<ContractResponse> contracts = new ArrayList<>();
        for (ContractSubscriptionRequest subscription : request.contracts()) {
            contracts.add(contractsService.create(toContractRequest(insured.id(), subscription)));
        }

        String fullName = insured.prenoms() + " " + insured.nom();
        String produits = contracts.stream()
                .map(ContractResponse::productName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
        boolean welcomeSmsSent = smsNotificationService.sendWelcomeSms(insured.clientPhone(), fullName, produits);

        int reminderSmsScheduled = 0;
        for (ContractResponse contract : contracts) {
            reminderSmsScheduled += smsNotificationService.scheduleContractReminders(
                    contract.id(), insured.clientPhone(), insured.clientPhone2(), contract.policyNumber(), contract.duree(), contract.dateEcheance());
        }

        return new InsuredRegistrationResponse(insured, contracts, welcomeSmsSent, reminderSmsScheduled);
    }

    private ContractRequest toContractRequest(Long insuredId, ContractSubscriptionRequest s) {
        return new ContractRequest(
                s.policyNumber(),
                s.dateEffet(),
                s.duree(),
                s.dateEcheance(),
                s.productId(),
                insuredId,
                null,
                s.partenaireId(),
                s.compagnieId(),
                s.premium(),
                s.commissionRate(),
                s.commissionAmount(),
                s.partnerShare(),
                s.status(),
                s.commissionStatus(),
                s.signedAt(),
                s.salesRepId(),
                s.notes(),
                null,
                s.modePaiement(),
                s.nombreEcheances(),
                s.periodiciteEcheances(),
                s.montantDejaVerse(),
                s.contratPrecedentId(),
                s.primeReversementStatus(),
                s.commercialCommissionAmount()
        );
    }
}
