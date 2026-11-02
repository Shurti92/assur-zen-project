package ci.assurzen.services;

import ci.assurzen.dto.LogTailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class LogService {

    private static final int DEFAULT_LINES = 500;
    private static final int MAX_LINES = 5000;

    @Value("${app.logs.file}")
    private String logFilePath;

    public LogTailResponse tail(Integer requestedLines) {
        int lines = clamp(requestedLines == null ? DEFAULT_LINES : requestedLines);

        Path path = Path.of(logFilePath);
        if (!Files.exists(path)) {
            return new LogTailResponse(logFilePath, 0, List.of());
        }

        List<String> allLines;
        try {
            allLines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le fichier de log: " + logFilePath, e);
        }

        int from = Math.max(0, allLines.size() - lines);
        List<String> tail = allLines.subList(from, allLines.size());
        return new LogTailResponse(logFilePath, tail.size(), tail);
    }

    private int clamp(int lines) {
        return Math.min(Math.max(lines, 1), MAX_LINES);
    }
}
