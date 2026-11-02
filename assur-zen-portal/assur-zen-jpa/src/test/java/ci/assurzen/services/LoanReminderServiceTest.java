package ci.assurzen.services;

import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.Loan;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.repositories.LoanInstallmentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre les rappels SMS d'echeance de pret (J-10/J-5/J-1 a 10h, J a 9h). */
class LoanReminderServiceTest {

    private LoanInstallment installment(long id, int number, LocalDate dueDate) {
        Partenaire partner = Partenaire.builder().id(1L).telephone("0700000000").build();
        Loan loan = Loan.builder().id(1L).loanReference("PRET2026000001").partner(partner).build();
        return LoanInstallment.builder().id(id).installmentNumber(number).dueDate(dueDate)
                .totalDue(300000L).status(InstallmentStatus.PENDING).loan(loan).build();
    }

    @Test
    void sendUpcomingReminders_echeancesAJ10J5J1_envoieUnSmsChacune() {
        LoanInstallmentRepository installmentRepository = mock(LoanInstallmentRepository.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        LoanInstallment i10 = installment(1L, 1, LocalDate.now().plusDays(10));
        LoanInstallment i5 = installment(2L, 2, LocalDate.now().plusDays(5));
        LoanInstallment i1 = installment(3L, 3, LocalDate.now().plusDays(1));
        when(installmentRepository.findByStatusInAndDueDateIn(any(), any())).thenReturn(List.of(i10, i5, i1));
        when(smsNotificationService.sendLoanInstallmentUpcomingSms(any(), any(), any(), any(), any(), anyLong())).thenReturn(true);

        LoanReminderService service = new LoanReminderService(installmentRepository, smsNotificationService);
        int count = service.sendUpcomingReminders();

        assertEquals(3, count);
        verify(smsNotificationService).sendLoanInstallmentUpcomingSms("0700000000", "PRET2026000001", 1, 300000L, i10.getDueDate(), 10L);
        verify(smsNotificationService).sendLoanInstallmentUpcomingSms("0700000000", "PRET2026000001", 2, 300000L, i5.getDueDate(), 5L);
        verify(smsNotificationService).sendLoanInstallmentUpcomingSms("0700000000", "PRET2026000001", 3, 300000L, i1.getDueDate(), 1L);
    }

    @Test
    void sendDueTodayReminders_echeanceAujourdhui_envoieUnSms() {
        LoanInstallmentRepository installmentRepository = mock(LoanInstallmentRepository.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        LoanInstallment today = installment(1L, 1, LocalDate.now());
        when(installmentRepository.findByStatusInAndDueDate(any(), any())).thenReturn(List.of(today));
        when(smsNotificationService.sendLoanInstallmentDueTodaySms(any(), any(), any(), any())).thenReturn(true);

        LoanReminderService service = new LoanReminderService(installmentRepository, smsNotificationService);
        int count = service.sendDueTodayReminders();

        assertEquals(1, count);
        verify(smsNotificationService).sendLoanInstallmentDueTodaySms("0700000000", "PRET2026000001", 1, 300000L);
    }

    @Test
    void sendUpcomingReminders_aucuneEcheanceCorrespondante_neSendRien() {
        LoanInstallmentRepository installmentRepository = mock(LoanInstallmentRepository.class);
        SmsNotificationService smsNotificationService = mock(SmsNotificationService.class);
        when(installmentRepository.findByStatusInAndDueDateIn(any(), any())).thenReturn(List.of());

        LoanReminderService service = new LoanReminderService(installmentRepository, smsNotificationService);
        int count = service.sendUpcomingReminders();

        assertEquals(0, count);
    }
}
