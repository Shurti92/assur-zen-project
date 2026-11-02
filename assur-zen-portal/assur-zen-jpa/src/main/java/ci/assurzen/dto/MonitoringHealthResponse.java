package ci.assurzen.dto;

import java.util.Map;

public record MonitoringHealthResponse(
        String status,
        Map<String, ComponentHealth> components
) {
    public record ComponentHealth(
            String status,
            Map<String, Object> details
    ) {
    }
}
