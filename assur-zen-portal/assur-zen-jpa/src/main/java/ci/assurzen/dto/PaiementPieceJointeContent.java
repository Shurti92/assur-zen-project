package ci.assurzen.dto;

public record PaiementPieceJointeContent(
        byte[] content,
        String contentType,
        String fileName
) {
}
