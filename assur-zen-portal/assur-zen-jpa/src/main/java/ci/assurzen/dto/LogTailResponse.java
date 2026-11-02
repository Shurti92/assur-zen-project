package ci.assurzen.dto;

import java.util.List;

public record LogTailResponse(
        String file,
        int lineCount,
        List<String> content
) {
}
