package ci.assurzen.services;

import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.repositories.LoanInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Detection des retards (§21/§56) : PENDING/PARTIALLY_PAID avec due_date < aujourd'hui -> OVERDUE.
 * Tache quotidienne, meme patron @Scheduled que GeoIpDatabaseUpdateScheduler. Une seule instance de
 * deploiement pour ce projet (pas de ShedLock/Quartz) - pas de verrou distribue necessaire, limite
 * documentee plutot qu'ignoree.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanOverdueService {

    private final LoanInstallmentRepository installmentRepository;

    @Transactional
    public int markOverdueInstallments() {
        List<LoanInstallment> retards = installmentRepository.findByStatusInAndDueDateBefore(
                List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIALLY_PAID), LocalDate.now());
        retards.forEach(i -> i.setStatus(InstallmentStatus.OVERDUE));
        installmentRepository.saveAll(retards);
        if (!retards.isEmpty()) {
            log.info("{} echeance(s) de pret marquee(s) OVERDUE", retards.size());
        }
        return retards.size();
    }

    @Scheduled(cron = "0 30 0 * * *")
    public void detecterRetardsQuotidien() {
        markOverdueInstallments();
    }
}
