package ci.assurzen.repositories;

import ci.assurzen.entities.NotificateurSms;
import ci.assurzen.entities.StatutEnvoiSms;
import ci.assurzen.entities.TypeMessageSms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NotificateurSmsRepository extends JpaRepository<NotificateurSms, Long> {

    List<NotificateurSms> findByDateEnvoiAndTypeMessageAndStatutEnvoi(
            LocalDate dateEnvoi,
            TypeMessageSms typeMessage,
            StatutEnvoiSms statutEnvoi
    );

    List<NotificateurSms> findByDateEnvoiLessThanEqualAndTypeMessageAndStatutEnvoi(
            LocalDate dateEnvoi,
            TypeMessageSms typeMessage,
            StatutEnvoiSms statutEnvoi
    );

    List<NotificateurSms> findByContractIdOrderByDateEnvoiAsc(Long contractId);

    List<NotificateurSms> findByContractIdInOrderByDateEnvoiAsc(List<Long> contractIds);

    void deleteByContractId(Long contractId);
}
