package ci.assurzen.dto;

public record GedDocumentVersionContent(
        byte[] content,
        String contentType,
        String fileName
) {
}
