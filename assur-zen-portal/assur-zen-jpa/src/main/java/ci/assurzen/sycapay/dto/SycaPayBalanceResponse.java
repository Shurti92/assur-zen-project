package ci.assurzen.sycapay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Reponse brute de POST /checkcashoutsolde.php. Schema confirme par un exemple reel (2026-09-22) :
 * un element "Resultats" par operateur active sur le compte marchand (Moov/Mtn/Orange/Wave), "solde"
 * en chaine avec 2 decimales (ex. "0.00"). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SycaPayBalanceResponse(
        @JsonProperty("Apiname") String apiName,
        @JsonProperty("Description") String description,
        @JsonProperty("Resultats") List<SycaPayBalanceEntry> resultats
) {
}
