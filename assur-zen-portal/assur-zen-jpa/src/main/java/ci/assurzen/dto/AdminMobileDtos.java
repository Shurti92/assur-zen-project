package ci.assurzen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Administration des comptes et demandes de l'application mobile (back-office, 2026-11-03). */
public final class AdminMobileDtos {

    private AdminMobileDtos() {
    }

    public record LinkedInsured(Long insuredId, String name, String phone) {
    }

    public record Account(
            Long userId,
            String phone,
            String fullName,
            boolean active,
            Instant createdAt,
            int linkedInsuredCount,
            /** Reference de la demande de suppression en attente, sinon null. */
            String pendingDeletionReference
    ) {
    }

    public record AccountDetail(Account account, List<LinkedInsured> linkedInsureds) {
    }

    public record AccountPage(List<Account> items, int page, int size, long totalElements, int totalPages) {
    }

    /** Creation d'un compte mobile pour un assure existant. phone est facultatif : par defaut le telephone de l'assure. */
    public record CreateAccountRequest(
            @NotNull(message = "L'assure est obligatoire") Long insuredId,
            @Pattern(regexp = "^$|" + MobileAuthDtos.PHONE_REGEX, message = MobileAuthDtos.PHONE_MESSAGE) String phone,
            Boolean sendInvitation
    ) {
    }

    public record ActiveRequest(@NotNull(message = "active est obligatoire") Boolean active, @Size(max = 500) String comment) {
    }

    public record LinkRequest(@NotNull(message = "L'assure est obligatoire") Long insuredId) {
    }

    public record DeletionRequestItem(
            Long id,
            String reference,
            Long userId,
            String phone,
            String fullName,
            String statut,
            Instant requestedAt,
            Instant processedAt,
            String processedBy,
            String comment
    ) {
    }

    /** decision : APPROVE (compte anonymise, confirm=true obligatoire) ou REJECT (compte reactive). */
    public record ProcessDeletionRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT", message = "decision doit valoir APPROVE ou REJECT") String decision,
            Boolean confirm,
            @Size(max = 500) String comment
    ) {
    }

    public record ContractRequestItem(
            Long id,
            String reference,
            Long contractId,
            String policyNumber,
            String insuredName,
            Long userId,
            String phone,
            String type,
            String message,
            String statut,
            Instant createdAt,
            Instant processedAt,
            String processedBy,
            String comment
    ) {
    }

    /** statut : TRAITEE ou REFUSEE (pour une demande de contrat ou un rappel : RAPPELE ou ANNULE). */
    public record ProcessRequest(
            @NotBlank @Pattern(regexp = "TRAITEE|REFUSEE|RAPPELE|ANNULE", message = "statut invalide") String statut,
            @Size(max = 500) String comment
    ) {
    }

    public record CallbackItem(
            Long id,
            String reference,
            String phone,
            String slot,
            String productId,
            Long userId,
            String statut,
            Instant createdAt,
            Instant processedAt,
            String processedBy,
            String comment
    ) {
    }
}
