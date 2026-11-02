package ci.assurzen.dto;

public record PartenaireDocumentContent(
        byte[] content,
        String contentType,
        String fileName
) {
}
