package ci.assurzen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contrats vus par l'application mobile (lot b, 2026-11-02, demande mobile n°3). */
public final class MobileContractDtos {

    private MobileContractDtos() {
    }

    /** Fenetre de renouvellement : eligible, date d'ouverture, jours restants avant l'echeance. */
    public record RenewalWindow(boolean eligible, LocalDate opensOn, Long daysLeft) {
    }

    public record ContractListItem(
            String id,
            String number,
            String productId,
            String productName,
            String insuredLabel,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Long premium,
            RenewalWindow renewal,
            Instant updatedAt
    ) {
    }

    public record ContractList(List<ContractListItem> items) {
    }

    public record Guarantee(String name, String kind, Long limit) {
    }

    public record DocumentRef(String id, String type, String name, String status) {
    }

    public record Attestation(boolean available, String verificationCode, LocalDate validFrom, LocalDate validUntil) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RenewalOffer(boolean eligible, LocalDate opensOn, Long daysLeft, Long amount, Integer durationMonths,
                               String payableReference) {
    }

    public record Actions(boolean canDeclareClaim, boolean canRequestChange, boolean canRequestTermination) {
    }

    public record ContractDetail(
            String id,
            String number,
            String productId,
            String productName,
            String insuredLabel,
            String holderName,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Long premium,
            List<Guarantee> guarantees,
            List<DocumentRef> documents,
            Attestation attestation,
            RenewalOffer renewal,
            Actions actions,
            Instant updatedAt
    ) {
    }
}
