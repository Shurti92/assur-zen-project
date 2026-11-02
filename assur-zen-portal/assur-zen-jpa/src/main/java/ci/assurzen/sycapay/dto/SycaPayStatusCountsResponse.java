package ci.assurzen.sycapay.dto;

import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Repartition des transactions SycaPay par statut (GET /sycapay/transactions/status-counts, pastilles
 * de filtre du frontend, 2026-09-23). Contrat frontend : les 6 cles de statut sont TOUJOURS presentes
 * (0 si aucune transaction) et total = somme des six. Cles JSON en majuscules = noms exacts de
 * SycaPayTransactionStatus, directement reutilisables comme valeur du filtre status de /transactions.
 */
public record SycaPayStatusCountsResponse(
        long total,
        @JsonProperty("PENDING") long pending,
        @JsonProperty("PROCESSING") long processing,
        @JsonProperty("SUCCESS") long success,
        @JsonProperty("FAILED") long failed,
        @JsonProperty("CANCELLED") long cancelled,
        @JsonProperty("UNKNOWN") long unknown
) {
    public static SycaPayStatusCountsResponse of(Map<SycaPayTransactionStatus, Long> counts) {
        long pending = counts.getOrDefault(SycaPayTransactionStatus.PENDING, 0L);
        long processing = counts.getOrDefault(SycaPayTransactionStatus.PROCESSING, 0L);
        long success = counts.getOrDefault(SycaPayTransactionStatus.SUCCESS, 0L);
        long failed = counts.getOrDefault(SycaPayTransactionStatus.FAILED, 0L);
        long cancelled = counts.getOrDefault(SycaPayTransactionStatus.CANCELLED, 0L);
        long unknown = counts.getOrDefault(SycaPayTransactionStatus.UNKNOWN, 0L);
        return new SycaPayStatusCountsResponse(pending + processing + success + failed + cancelled + unknown,
                pending, processing, success, failed, cancelled, unknown);
    }
}
