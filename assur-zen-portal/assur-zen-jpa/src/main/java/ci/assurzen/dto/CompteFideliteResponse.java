package ci.assurzen.dto;

import ci.assurzen.entities.StatutVip;

import java.time.Instant;
import java.time.LocalDate;

public record CompteFideliteResponse(
        Long id,
        Long insuredId,
        String insuredName,
        Long soldePoints,
        Long pointsCumulesTotal,
        Long palierId,
        String palierLibelle,
        LocalDate dateAdhesion,
        Long scoreFidelite,
        StatutVip statutVip,
        Instant dateEntreeVip,
        Instant dateSortieVip,
        Long gestionnaireDedieId,
        String gestionnaireDedieNom,
        boolean active
) {
}
