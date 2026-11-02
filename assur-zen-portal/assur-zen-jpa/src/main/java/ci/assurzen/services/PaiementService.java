package ci.assurzen.services;

import ci.assurzen.dto.PaiementPieceJointeContent;
import ci.assurzen.dto.PaiementPieceJointeResponse;
import ci.assurzen.dto.PaiementRequest;
import ci.assurzen.dto.PaiementResponse;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Echeance;
import ci.assurzen.entities.Paiement;
import ci.assurzen.entities.PaiementPieceJointe;
import ci.assurzen.entities.StatutEcheance;
import ci.assurzen.entities.User;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.EcheanceRepository;
import ci.assurzen.repositories.PaiementPieceJointeRepository;
import ci.assurzen.repositories.PaiementRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Enregistrement des paiements de prime recus sur un contrat. Module minimal (n'existait pas du tout
 * avant) : juste ce qu'il faut pour tracer un encaissement et declencher l'ecriture comptable associee.
 */
@Service
@RequiredArgsConstructor
public class PaiementService {

    private final PaiementRepository paiementRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final LedgerService ledgerService;
    private final BusinessMetrics businessMetrics;
    private final EcheancierService echeancierService;
    private final EcheanceRepository echeanceRepository;
    private final FideliteService fideliteService;
    private final PaiementPieceJointeRepository paiementPieceJointeRepository;
    private final PaiementStorageService paiementStorageService;

    @Transactional
    public PaiementResponse enregistrer(Long contractId, PaiementRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + contractId));

        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User recordedBy = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        Echeance echeance = null;
        if (request.echeanceId() != null) {
            echeance = echeancierService.getEcheance(request.echeanceId());
            if (!echeance.getContract().getId().equals(contractId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette echeance n'appartient pas au contrat " + contractId);
            }
            if (echeance.getStatut() == StatutEcheance.PAYEE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette echeance est deja payee");
            }
        }

        Paiement paiement = Paiement.builder()
                .contract(contract)
                .montant(request.montant())
                .datePaiement(request.datePaiement() != null ? request.datePaiement() : LocalDate.now())
                .moyenPaiement(request.moyenPaiement())
                .reference(request.reference())
                .recordedBy(recordedBy)
                .build();
        paiement = paiementRepository.save(paiement);

        if (echeance != null) {
            echeancierService.marquerPayee(echeance, paiement);
        }

        ledgerService.enregistrerPaiement(paiement);
        businessMetrics.incrementPaymentsSuccessful();
        fideliteService.crediterPourPaiement(contract, paiement.getMontant());

        return toResponse(paiement);
    }

    public List<PaiementResponse> listForContract(Long contractId) {
        return paiementRepository.findByContractIdOrderByDatePaiementAsc(contractId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PaiementPieceJointeResponse> listPiecesJointes(Long contractId, Long paiementId) {
        getPaiement(contractId, paiementId);
        return paiementPieceJointeRepository.findByPaiementIdOrderByCreatedAtAsc(paiementId).stream()
                .map(this::toPieceJointeResponse)
                .toList();
    }

    @Transactional
    public PaiementPieceJointeResponse ajouterPieceJointe(Long contractId, Long paiementId, MultipartFile file) {
        Paiement paiement = getPaiement(contractId, paiementId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier vide ou absent");
        }

        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        User uploadedBy = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));

        String storedPath;
        try {
            storedPath = paiementStorageService.store(paiementId, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Echec de l'enregistrement de la piece jointe", e);
        }

        PaiementPieceJointe piece = PaiementPieceJointe.builder()
                .paiement(paiement)
                .fileName(file.getOriginalFilename())
                .storedPath(storedPath)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .uploadedBy(uploadedBy)
                .build();

        return toPieceJointeResponse(paiementPieceJointeRepository.save(piece));
    }

    public PaiementPieceJointeContent getPieceJointeContent(Long contractId, Long paiementId, Long pieceId) {
        getPaiement(contractId, paiementId);
        PaiementPieceJointe piece = paiementPieceJointeRepository.findByIdAndPaiementId(pieceId, paiementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Piece jointe introuvable: " + pieceId));
        return new PaiementPieceJointeContent(paiementStorageService.read(piece.getStoredPath()), piece.getContentType(), piece.getFileName());
    }

    private Paiement getPaiement(Long contractId, Long paiementId) {
        Paiement paiement = paiementRepository.findById(paiementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable: " + paiementId));
        if (!paiement.getContract().getId().equals(contractId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce paiement n'appartient pas au contrat " + contractId);
        }
        return paiement;
    }

    private PaiementPieceJointeResponse toPieceJointeResponse(PaiementPieceJointe p) {
        return new PaiementPieceJointeResponse(
                p.getId(),
                p.getPaiement().getId(),
                p.getFileName(),
                p.getContentType(),
                p.getSizeBytes(),
                p.getUploadedBy() != null ? p.getUploadedBy().getUsername() : null,
                p.getCreatedAt());
    }

    private PaiementResponse toResponse(Paiement paiement) {
        Long echeanceId = echeanceRepository.findByPaiementId(paiement.getId()).map(Echeance::getId).orElse(null);
        return new PaiementResponse(
                paiement.getId(),
                paiement.getContract().getId(),
                paiement.getContract().getPolicyNumber(),
                paiement.getMontant(),
                paiement.getDatePaiement(),
                paiement.getMoyenPaiement(),
                paiement.getReference(),
                paiement.getRecordedBy().getId(),
                paiement.getRecordedBy().getUsername(),
                paiement.getCreation(),
                echeanceId);
    }
}
