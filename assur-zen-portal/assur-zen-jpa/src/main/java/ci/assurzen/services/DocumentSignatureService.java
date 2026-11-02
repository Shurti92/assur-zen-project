package ci.assurzen.services;

/**
 * Preparation future (§58) : integration signature electronique. Aucune implementation ni provider
 * specifique dans cette phase - pas de bean Spring, pas d'appel depuis GedDocumentService.
 */
public interface DocumentSignatureService {

    enum SignatureStatus { SIGNATURE_REQUESTED, SIGNATURE_IN_PROGRESS, SIGNED, SIGNATURE_REJECTED }

    SignatureStatus requestSignature(Long documentId, Long versionId);
}
