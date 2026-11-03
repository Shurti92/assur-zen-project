package ci.assurzen.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Sinistres vus par l'application mobile (demande n°6, 2026-11-02). */
public final class MobileClaimDtos {

    private MobileClaimDtos() {
    }

    public record ClaimType(String id, String label) {
    }

    public record ClaimTypeList(List<ClaimType> items) {
    }

    public record ThirdParty(
            @Size(max = 150) String name,
            @Size(max = 30) String plate,
            @Size(max = 150) String insurer
    ) {
    }

    public record CreateRequest(
            @NotBlank(message = "Le contrat est obligatoire") String contractId,
            @NotBlank(message = "Le type de sinistre est obligatoire") @Size(max = 40) String typeId,
            @NotNull(message = "La date du sinistre est obligatoire") LocalDate occurredAt,
            @Size(max = 300) String place,
            Boolean thirdPartyInvolved,
            @Valid ThirdParty thirdParty,
            @Size(max = 4000) String description
    ) {
    }

    /** expectedHandlingHours est null tant que CLAIM_HANDLING_HOURS n'est pas configure (aucun engagement). */
    public record Created(String id, String number, String status, LocalDate declaredAt, Integer expectedHandlingHours) {
    }

    public record Step(String label, /** DONE, CURRENT ou TODO */ String state) {
    }

    public record HistoryEntry(Instant date, String text) {
    }

    public record Claim(
            String id,
            String number,
            String typeLabel,
            String contractId,
            String contractLabel,
            LocalDate declaredAt,
            LocalDate occurredAt,
            String status,
            boolean closed,
            int currentStep,
            List<Step> steps,
            List<HistoryEntry> history,
            boolean canAddFiles,
            int photoCount,
            int documentCount
    ) {
    }

    public record ClaimList(List<Claim> items) {
    }

    public record FileRef(String id, String kind, String name, Long sizeBytes, Instant uploadedAt) {
    }

    public record FileList(List<FileRef> items, int maxPhotos, int maxDocuments) {
    }
}
