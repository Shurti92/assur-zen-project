package ci.assurzen.dto;

public record VipParNiveauItem(
        Long palierId,
        String palierLibelle,
        String couleur,
        String icone,
        boolean estVip,
        long nombreComptes
) {
}
