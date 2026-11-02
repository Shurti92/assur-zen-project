package ci.assurzen.services;

import ci.assurzen.entities.InstallmentStatus;
import ci.assurzen.entities.LoanInstallment;
import ci.assurzen.repositories.LoanInstallmentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanOverdueServiceTest {

    @Test
    void markOverdueInstallments_echeancesEnRetard_passentAOverdue() {
        LoanInstallmentRepository repository = mock(LoanInstallmentRepository.class);
        LoanInstallment retard = LoanInstallment.builder().id(1L).installmentNumber(1)
                .dueDate(LocalDate.now().minusDays(5)).status(InstallmentStatus.PENDING).build();
        when(repository.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of(retard));
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanOverdueService service = new LoanOverdueService(repository);
        int count = service.markOverdueInstallments();

        assertEquals(1, count);
        assertEquals(InstallmentStatus.OVERDUE, retard.getStatus());
        verify(repository).saveAll(List.of(retard));
    }

    @Test
    void markOverdueInstallments_aucuneEcheanceEnRetard_neFaitRien() {
        LoanInstallmentRepository repository = mock(LoanInstallmentRepository.class);
        when(repository.findByStatusInAndDueDateBefore(any(), any())).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        LoanOverdueService service = new LoanOverdueService(repository);
        int count = service.markOverdueInstallments();

        assertEquals(0, count);
    }
}
