package ci.assurzen.dto;

/** durationMinutes : null/absent = blocage indefini (jusqu'a deblocage manuel explicite). */
public record BlockIpRequest(Integer durationMinutes) {
}
