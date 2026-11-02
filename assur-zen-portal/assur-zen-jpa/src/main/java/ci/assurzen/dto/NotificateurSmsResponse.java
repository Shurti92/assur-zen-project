package ci.assurzen.dto;

import ci.assurzen.entities.StatutEnvoiSms;
import ci.assurzen.entities.TypeMessageSms;

import java.time.LocalDate;

public record NotificateurSmsResponse(
        Long id,
        Long contractId,
        String telephoneAssure,
        String message,
        TypeMessageSms typeMessage,
        LocalDate dateEnvoi,
        StatutEnvoiSms statutEnvoi,
        String responseProvider
) {
}
