package ci.assurzen.dto;

public record VipTopClientItem(
        Long insuredId,
        String insuredName,
        Long scoreFidelite,
        String palierLibelle,
        Long soldePoints
) {
}
