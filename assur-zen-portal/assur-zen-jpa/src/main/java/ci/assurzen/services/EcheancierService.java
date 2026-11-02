package ci.assurzen.services;

import ci.assurzen.dto.ContractFractionneSummaryResponse;
import ci.assurzen.dto.EcheanceResponse;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Echeance;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.ModePaiement;
import ci.assurzen.entities.Paiement;
import ci.assurzen.entities.PeriodiciteEcheance;
import ci.assurzen.entities.StatutEcheance;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.EcheanceRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EcheancierService {

    private final EcheanceRepository echeanceRepository;
    private final ContractRepository contractRepository;
    private final AuthService authService;

    /**
     * Genere l'echeancier d'un contrat fractionne : le solde restant a financer (premium moins
     * montantDejaVerse, s'il est renseigne) est reparti en parts egales sur nombreEcheances, le reste
     * de la division entiere etant porte par la derniere echeance afin que la somme des echeances
     * soit toujours exactement egale au solde restant.
     */
    @Transactional
    public List<Echeance> genererEcheancier(Contract contract) {
        int n = contract.getNombreEcheances();
        PeriodiciteEcheance periodicite = contract.getPeriodiciteEcheances();
        long dejaVerse = contract.getMontantDejaVerse() == null ? 0L : contract.getMontantDejaVerse();
        long total = Math.max(0L, contract.getPremium() - dejaVerse);
        long base = total / n;
        long remainder = total - (base * n);

        LocalDate depart = contract.getDateEffet() != null ? contract.getDateEffet()
                : (contract.getSignedAt() != null ? contract.getSignedAt() : LocalDate.now());

        List<Echeance> echeances = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            long montant = base + (i == n ? remainder : 0);
            LocalDate dateEcheance = depart.plusMonths((long) periodicite.getMoisEntreEcheances() * i);
            echeances.add(Echeance.builder()
                    .contract(contract)
                    .numeroEcheance(i)
                    .montant(montant)
                    .dateEcheance(dateEcheance)
                    .statut(StatutEcheance.EN_ATTENTE)
                    .build());
        }
        return echeanceRepository.saveAll(echeances);
    }

    public List<EcheanceResponse> listForContract(Long contractId) {
        LocalDate today = LocalDate.now();
        return echeanceRepository.findByContractIdOrderByNumeroEcheanceAsc(contractId).stream()
                .map(e -> toResponse(e, today))
                .toList();
    }

    /**
     * Marque une echeance comme payee et la lie au paiement recu. Appele par PaiementService lorsque
     * le paiement enregistre reference explicitement une echeance de l'echeancier.
     */
    @Transactional
    public void marquerPayee(Echeance echeance, Paiement paiement) {
        echeance.setStatut(StatutEcheance.PAYEE);
        echeance.setPaiement(paiement);
        echeanceRepository.save(echeance);
    }

    public Echeance getEcheance(Long echeanceId) {
        return echeanceRepository.findById(echeanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Echeance introuvable: " + echeanceId));
    }

    /**
     * Vue de suivi transversale de tous les contrats en paiement fractionne, pour permettre le suivi
     * de ce type de contrat (echeances payees/en attente/en retard, reste a payer, prochaine echeance).
     */
    public List<ContractFractionneSummaryResponse> listFractionnes() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        boolean admin = principal.roles() != null && principal.roles().toUpperCase(Locale.ROOT).contains("ADMIN");
        LocalDate today = LocalDate.now();
        return contractRepository.findAll().stream()
                .filter(c -> c.getModePaiement() == ModePaiement.FRACTIONNE)
                .filter(c -> admin || (c.getSalesRep() != null && c.getSalesRep().getId().equals(principal.userId())))
                .map(c -> toSummary(c, today))
                .toList();
    }

    private ContractFractionneSummaryResponse toSummary(Contract contract, LocalDate today) {
        List<Echeance> echeances = echeanceRepository.findByContractIdOrderByNumeroEcheanceAsc(contract.getId());

        long payees = echeances.stream().filter(e -> e.getStatut() == StatutEcheance.PAYEE).count();
        long enRetard = echeances.stream()
                .filter(e -> e.getStatut() == StatutEcheance.EN_ATTENTE && e.getDateEcheance().isBefore(today))
                .count();
        long enAttente = echeances.size() - payees;
        long dejaVerse = contract.getMontantDejaVerse() == null ? 0L : contract.getMontantDejaVerse();
        // Inclut le montant deja verse a la souscription (deduit de l'echeancier a la generation,
        // voir genererEcheancier) pour que "paye"/"restant du" restent coherents avec ce que le
        // client a reellement deja verse, pas seulement les echeances marquees PAYEE depuis.
        long montantPaye = dejaVerse + echeances.stream()
                .filter(e -> e.getStatut() == StatutEcheance.PAYEE)
                .mapToLong(Echeance::getMontant)
                .sum();

        LocalDate prochaine = echeances.stream()
                .filter(e -> e.getStatut() == StatutEcheance.EN_ATTENTE)
                .map(Echeance::getDateEcheance)
                .min(LocalDate::compareTo)
                .orElse(null);

        return new ContractFractionneSummaryResponse(
                contract.getId(),
                contract.getPolicyNumber(),
                insuredName(contract.getInsured()),
                contract.getPremium(),
                contract.getNombreEcheances(),
                contract.getPeriodiciteEcheances(),
                payees,
                enAttente,
                enRetard,
                montantPaye,
                contract.getPremium() - montantPaye,
                prochaine);
    }

    private String insuredName(Insured insured) {
        if (insured == null) {
            return null;
        }
        return insured.getPrenoms() + " " + insured.getNom();
    }

    private EcheanceResponse toResponse(Echeance e, LocalDate today) {
        boolean enRetard = e.getStatut() == StatutEcheance.EN_ATTENTE && e.getDateEcheance().isBefore(today);
        return new EcheanceResponse(
                e.getId(),
                e.getContract().getId(),
                e.getContract().getPolicyNumber(),
                e.getNumeroEcheance(),
                e.getMontant(),
                e.getDateEcheance(),
                e.getStatut(),
                enRetard,
                e.getPaiement() == null ? null : e.getPaiement().getId());
    }
}
