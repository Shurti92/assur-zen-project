package ci.assurzen.sycapay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Reponse brute de POST /authentification. code=0 => succes (cf. section VII de la documentation
 * SycaPay : "0 / Success, Pending"). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SycaPayAuthResponse(
        Integer code,
        String token,
        String description
) {
    public boolean isSuccess() {
        return code != null && code == 0 && token != null && !token.isBlank();
    }
}
