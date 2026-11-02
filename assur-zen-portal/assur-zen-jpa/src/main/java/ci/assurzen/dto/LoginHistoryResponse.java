package ci.assurzen.dto;

import ci.assurzen.entities.DeviceType;

import java.time.Instant;

public record LoginHistoryResponse(
        Long id,
        Long userId,
        String username,
        Long partenaireId,
        String ip,
        DeviceType deviceType,
        String browser,
        String os,
        String country,
        String city,
        String isp,
        Instant dateHeure
) {
}
