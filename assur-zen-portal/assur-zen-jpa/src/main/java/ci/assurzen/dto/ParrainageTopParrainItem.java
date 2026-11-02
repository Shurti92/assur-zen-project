package ci.assurzen.dto;

public record ParrainageTopParrainItem(
        Long parrainId,
        String parrainNom,
        long nombreParrainages,
        long nombreConvertis,
        long recompensesTotal
) {
}
