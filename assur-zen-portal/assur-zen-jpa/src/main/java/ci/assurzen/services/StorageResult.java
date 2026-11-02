package ci.assurzen.services;

public record StorageResult(String storedPath, String storedFileName, long sizeBytes, String checksumSha256) {
}
