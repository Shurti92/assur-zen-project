package ci.assurzen.dto;

public record SinistrePhotoContent(
        byte[] content,
        String contentType,
        String fileName
) {
}
