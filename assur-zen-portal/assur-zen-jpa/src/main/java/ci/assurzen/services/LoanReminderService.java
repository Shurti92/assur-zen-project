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
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Rappels SMS d'echeance de pret, a heure fixe (demande explicite : 10h pour J-10/J-5/J-1, 9h pour
 * le jour meme). Pas de reutilisation du mecanisme NotificateurSms/date_envoi existant
 * (scheduleContractReminders/scheduleEcheanceReminders) : celui-ci ne stocke qu'une LocalDate, sans
 * granularite d'heure, incompatible avec l'exigence d'heures precises differentes selon le rappel.
 * Meme patron @Scheduled que LoanOverdueService/GeoIpDatabaseUpdateScheduler - une seule instance de
 * deploiement, pas de verrou distribue necessaire.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanReminderService {

    private static final List<InstallmentStatus> OPEN_STATUSES = List.of(InstallmentStatus.PENDING, InstallmentStatus.PARTIALLY_PAID);
    private static final List<Long> UPCOMING_OFFSETS_DAYS = List.of(10L, 5L, 1L);

    private final LoanInstallmentRepository installmentRepository;
    private final SmsNotificationService smsNotificationService;

    /** J-10, J-5, J-1 a 10h. */
    @Transactional(readOnly = true)
    public int sendUpcomingReminders() {
        LocalDate today = LocalDate.now();
        List<LocalDate> targetDates = UPCOMING_OFFSETS_DAYS.stream().map(today::plusDays).toList();
        List<LoanInstallment> installments = installmentRepository.findByStatusInAndDueDateIn(OPEN_STATUSES, targetDates);

        int count = 0;
        for (LoanInstallment installment : installments) {
            long joursAvant = ChronoUnit.DAYS.between(today, installment.getDueDate());
            if (sendReminder(installment, joursAvant)) {
                count++;
            }
        }
        log.info("{} rappel(s) SMS d'echeance a venir envoye(s) (J-10/J-5/J-1)", count);
        return count;
    }

    /** Jour J a 9h. */
    @Transactional(readOnly = true)
    public int sendDueTodayReminders() {
        List<LoanInstallment> installments = installmentRepository.findByStatusInAndDueDate(OPEN_STATUSES, LocalDate.now());

        int count = 0;
        for (LoanInstallment installment : installments) {
            if (sendDueTodayReminder(installment)) {
                count++;
            }
        }
        log.info("{} rappel(s) SMS d'echeance du jour envoye(s)", count);
        return count;
    }

    private boolean sendReminder(LoanInstallment installment, long joursAvant) {
        try {
            String phone = installment.getLoan().getPartner().getTelephone();
            return smsNotificationService.sendLoanInstallmentUpcomingSms(phone, installment.getLoan().getLoanReference(),
                    installment.getInstallmentNumber(), installment.getTotalDue(), installment.getDueDate(), joursAvant);
        } catch (RuntimeException e) {
            log.error("Echec du rappel d'echeance a venir pour l'echeance {}", installment.getId(), e);
            return false;
        }
    }

    private boolean sendDueTodayReminder(LoanInstallment installment) {
        try {
            String phone = installment.getLoan().getPartner().getTelephone();
            return smsNotificationService.sendLoanInstallmentDueTodaySms(phone, installment.getLoan().getLoanReference(),
                    installment.getInstallmentNumber(), installment.getTotalDue());
        } catch (RuntimeException e) {
            log.error("Echec du rappel d'echeance du jour pour l'echeance {}", installment.getId(), e);
            return false;
        }
    }

    @Scheduled(cron = "0 0 10 * * *")
    public void rappelsAVenirQuotidien() {
        sendUpcomingReminders();
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void rappelsJourJQuotidien() {
        sendDueTodayReminders();
    }
}
