package ci.assurzen.dto;

import java.util.List;

public record ImportResult(
        int totalRows,
        int successCount,
        int errorCount,
        List<ImportRowError> errors
) {
}
