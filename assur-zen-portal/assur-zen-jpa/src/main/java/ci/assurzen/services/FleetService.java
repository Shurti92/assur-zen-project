package ci.assurzen.services;

import ci.assurzen.audit.Auditable;
import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.AddVehicleRequest;
import ci.assurzen.dto.AttachContractRequest;
import ci.assurzen.dto.FleetContractResponse;
import ci.assurzen.dto.FleetEcheanceResponse;
import ci.assurzen.dto.NotificateurSmsResponse;
import ci.assurzen.dto.FleetMovementResponse;
import ci.assurzen.dto.FleetRequest;
import ci.assurzen.dto.FleetResponse;
import ci.assurzen.dto.FleetVehicleResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.dto.RemoveVehicleRequest;
import ci.assurzen.dto.ReplaceVehicleRequest;
import ci.assurzen.dto.TransferVehicleRequest;
import ci.assurzen.entities.ActionAudit;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.Fleet;
import ci.assurzen.entities.FleetMovement;
import ci.assurzen.entities.FleetMovementType;
import ci.assurzen.entities.FleetStatus;
import ci.assurzen.entities.FleetType;
import ci.assurzen.entities.FleetVehicle;
import ci.assurzen.entities.FleetVehicleStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.TypeMessageSms;
import ci.assurzen.entities.User;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.enums.CodePrefix;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.FleetMovementRepository;
import ci.assurzen.repositories.FleetRepository;
import ci.assurzen.repositories.FleetVehicleRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.ProduitRepository;
import ci.assurzen.repositories.ProfileRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.repositories.VehiculeRepository;
import ci.assurzen.security.AuthenticatedUserPrincipal;
import ci.assurzen.service.AuthService;
import static ci.assurzen.utils.SafeAccess.safe;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FleetService {

    private final FleetRepository fleetRepository;
    private final FleetVehicleRepository fleetVehicleRepository;
    private final FleetMovementRepository fleetMovementRepository;
    private final VehiculeRepository vehiculeRepository;
    private final InsuredRepository insuredRepository;
    private final ProduitRepository produitRepository;
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final CodeGeneratorService codeGeneratorService;
    private final AuthService authService;
    private final FunctionalAuditRecorder functionalAuditRecorder;
    private final SmsNotificationService smsNotificationService;

    private static final String TEMPLATE_VEHICULE_INCORPORE_FLOTTE = "VEHICULE_INCORPORE_FLOTTE";
    /** Ajout unitaire (2026-09-13) : message detaille (type/marque/modele + n de police si un contrat
     * existe deja pour ce vehicule) - distinct du template agrege ci-dessus, reserve a l'import en
     * masse ou detailler chaque ligne dans un seul SMS n'aurait pas de sens. */
    private static final String TEMPLATE_VEHICULE_FLOTTE_DETAIL = "VEHICULE_FLOTTE_DETAIL";

    // ---------------------------------------------------------------- Flottes : CRUD + cycle de vie

    public PageResponse<FleetResponse> search(String fleetNumber, Long clientId, String name, FleetType fleetType,
                                               FleetStatus status, LocalDate startDate, LocalDate endDate,
                                               Integer page, Integer size) {
        return paginate(filter(fleetNumber, clientId, name, fleetType, status, startDate, endDate), page, size);
    }

    public FleetResponse getFleet(Long id) {
        return toResponse(resolveFleet(id));
    }

    @Transactional
    @Auditable(action = ActionAudit.CREATE, module = "FLOTTE", entityName = "Fleet")
    public FleetResponse create(FleetRequest request) {
        String fleetNumber = StringUtils.hasText(request.fleetNumber())
                ? request.fleetNumber()
                : codeGeneratorService.generate(CodePrefix.FLOTTE);
        fleetRepository.findByFleetNumber(fleetNumber).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une flotte existe deja pour ce numero: " + fleetNumber);
        });

        Fleet fleet = Fleet.builder()
                .fleetNumber(fleetNumber)
                .client(resolveInsured(request.clientId()))
                .name(request.name())
                .description(request.description())
                .fleetType(request.fleetType())
                .produit(request.produitId() != null ? resolveProduit(request.produitId()) : null)
                .status(FleetStatus.DRAFT)
                .startDate(request.startDate())
                .endDate(request.endDate())
                .managerName(request.managerName())
                .managerPhone(request.managerPhone())
                .managerEmail(request.managerEmail())
                .build();

        return toResponse(fleetRepository.save(fleet));
    }

    @Transactional
    public FleetResponse update(Long id, FleetRequest request) {
        Fleet fleet = resolveFleet(id);
        if (fleet.getStatus() == FleetStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flotte cloturee, modification impossible");
        }
        FleetResponse avant = toResponse(fleet);

        if (StringUtils.hasText(request.fleetNumber()) && !request.fleetNumber().equals(fleet.getFleetNumber())) {
            fleetRepository.findByFleetNumber(request.fleetNumber())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Une flotte existe deja pour ce numero: " + request.fleetNumber());
                    });
            fleet.setFleetNumber(request.fleetNumber());
        }
        fleet.setClient(resolveInsured(request.clientId()));
        fleet.setName(request.name());
        fleet.setDescription(request.description());
        fleet.setFleetType(request.fleetType());
        fleet.setProduit(request.produitId() != null ? resolveProduit(request.produitId()) : null);
        fleet.setStartDate(request.startDate());
        fleet.setEndDate(request.endDate());
        fleet.setManagerName(request.managerName());
        fleet.setManagerPhone(request.managerPhone());
        fleet.setManagerEmail(request.managerEmail());

        FleetResponse apres = toResponse(fleetRepository.save(fleet));
        functionalAuditRecorder.recordUpdate("FLOTTE", "Fleet", String.valueOf(id), avant, apres, null);
        return apres;
    }

    @Transactional
    @Auditable(action = ActionAudit.ACTIVATE, module = "FLOTTE", entityName = "Fleet")
    public FleetResponse activate(Long id) {
        Fleet fleet = resolveFleet(id);
        if (fleet.getStatus() == FleetStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flotte cloturee, aucune transition possible");
        }
        if (fleet.getStatus() == FleetStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flotte deja active");
        }
        fleet.setStatus(FleetStatus.ACTIVE);
        return toResponse(fleetRepository.save(fleet));
    }

    @Transactional
    @Auditable(action = ActionAudit.SUSPEND, module = "FLOTTE", entityName = "Fleet")
    public FleetResponse suspend(Long id) {
        Fleet fleet = resolveFleet(id);
        if (fleet.getStatus() != FleetStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seule une flotte active peut etre suspendue (statut actuel: " + fleet.getStatus() + ")");
        }
        fleet.setStatus(FleetStatus.SUSPENDED);
        return toResponse(fleetRepository.save(fleet));
    }

    @Transactional
    @Auditable(action = ActionAudit.CLOSE, module = "FLOTTE", entityName = "Fleet")
    public FleetResponse close(Long id) {
        Fleet fleet = resolveFleet(id);
        if (fleet.getStatus() == FleetStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flotte deja cloturee");
        }
        fleet.setStatus(FleetStatus.CLOSED);
        return toResponse(fleetRepository.save(fleet));
    }

    /** Suppression logique, reservee aux flottes jamais activees. Une flotte DRAFT n'a jamais pu
     * recevoir de vehicule/mouvement/import (requireFleetActive l'exige partout ailleurs), donc ce
     * seul controle de statut suffit - pas besoin de verifier separement l'absence de vehicules. */
    @Transactional
    @Auditable(action = ActionAudit.DELETE, module = "FLOTTE", entityName = "Fleet")
    public void delete(Long id) {
        Fleet fleet = resolveFleet(id);
        if (fleet.getStatus() != FleetStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une flotte deja activee ne peut pas etre supprimee, cloturez-la a la place (statut actuel: " + fleet.getStatus() + ")");
        }
        fleet.setDeleted(true);
        fleetRepository.save(fleet);
    }

    // ---------------------------------------------------------------- Mouvements de vehicules

    public PageResponse<FleetVehicleResponse> listVehicles(Long fleetId, FleetVehicleStatus status, VehiculeType vehiculeType,
                                                            Integer page, Integer size) {
        resolveFleet(fleetId);
        List<FleetVehicle> rows = status != null
                ? fleetVehicleRepository.findByFleetIdAndStatusOrderByCreatedAtDesc(fleetId, status)
                : fleetVehicleRepository.findByFleetIdOrderByCreatedAtDesc(fleetId);
        // fv.getVehicule() peut etre null (vehicule depuis supprime, @NotFound(IGNORE)) - une ligne
        // ainsi orpheline n'a pas de type connu, elle est exclue du filtre par type (mais reste visible
        // sans filtre, via toVehicleResponse() qui gere deja le null). Meme classe de bug que
        // listContracts()/listEcheances() ci-dessus, meme jour.
        List<FleetVehicleResponse> filtered = rows.stream()
                .filter(fv -> vehiculeType == null || (fv.getVehicule() != null && fv.getVehicule().getType() == vehiculeType))
                .map(this::toVehicleResponse)
                .toList();
        return paginate(filtered, page, size);
    }

    @Transactional
    public FleetVehicleResponse addVehicle(Long fleetId, AddVehicleRequest request) {
        Fleet fleet = resolveFleet(fleetId);
        requireFleetActive(fleet);

        Vehicule vehicule = resolveVehicule(request.vehiculeId());
        FleetVehicle fleetVehicle = ajouterVehiculeAuCoeur(fleet, vehicule, request.reason());
        recomputeCounters(fleet);
        Boolean notificationEnvoyee = notifierIncorporationUnitaire(fleet, vehicule);

        return toVehicleResponse(fleetVehicle, notificationEnvoyee);
    }

    /** Coeur partage de l'ajout d'un vehicule a une flotte (eligibilite + liaison + mouvement + audit),
     * utilise par addVehicle (unitaire) ET par VehiculeImportService.confirm (import en masse, ligne
     * par ligne, chaque appel dans sa propre transaction REQUIRES_NEW) - pour ne jamais faire diverger
     * les regles entre les deux flux. Ne recalcule PAS les compteurs de la flotte : a la charge de
     * l'appelant (une fois par appel unitaire, une seule fois apres toute une boucle d'import). */
    FleetVehicle ajouterVehiculeAuCoeur(Fleet fleet, Vehicule vehicule, String motif) {
        requireFleetActive(fleet);
        requireVehiculeEligible(vehicule, fleet);

        FleetVehicle fleetVehicle;
        try {
            fleetVehicle = fleetVehicleRepository.saveAndFlush(FleetVehicle.builder()
                    .fleet(fleet)
                    .vehicule(vehicule)
                    .status(FleetVehicleStatus.ACTIVE)
                    .dateAdded(LocalDate.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicule deja actif dans une flotte (conflit concurrent)");
        }

        vehicule.setCurrentFleet(fleet);
        vehiculeRepository.save(vehicule);

        recordMovement(fleet, vehicule, FleetMovementType.ADD, motif, null, null, null, null);
        functionalAuditRecorder.recordAction("FLOTTE", "Vehicule", String.valueOf(vehicule.getId()), ActionAudit.ADD,
                "Vehicule " + libelleVehicule(vehicule) + " ajoute a la flotte " + fleet.getFleetNumber());

        return fleetVehicle;
    }

    /**
     * SMS d'incorporation (contrat "SMS d'incorporation flotte", 2026-09-08) - au gestionnaire de la
     * flotte (Fleet.managerPhone) ou, a defaut, au client (Fleet.client.clientPhone). Package-private :
     * reutilisee par VehiculeImportService.confirm() pour un SMS UNIQUE agrege sur tout le lot importe
     * (nombreVehicules = nombre de lignes reussies), jamais un SMS par ligne - meme convention
     * d'agregation que CommissionService/CommercialCommissionService. Echec d'envoi non bloquant
     * (sendFromTemplate ne leve jamais). Renvoie null si aucun SMS n'a ete tente (0 vehicule ou aucun
     * telephone disponible) - distinct de false (tentative faite, echec constate), meme nuance que
     * partout ailleurs ou ce champ n'est peuple que si un envoi a reellement ete tente.
     */
    /** Reservee a l'appel interne depuis notifierIncorporationApresImport() (VehiculeImportService.confirm(),
     * ni transactionnel ni proprietaire d'une session active a cet instant de son execution - une
     * flotte sans managerPhone y provoquait un LazyInitializationException reel sur fleet.getClient(),
     * bug trouve en direct le 2026-09-13). Ne PAS appeler directement avec un Fleet potentiellement
     * detache hors transaction - passer par notifierIncorporationApresImport(fleetId, ...) a la place. */
    Boolean notifierIncorporation(Fleet fleet, int nombreVehicules) {
        if (nombreVehicules <= 0) {
            return null;
        }
        String telephone = telephoneGestionnaire(fleet);
        if (!StringUtils.hasText(telephone)) {
            return null;
        }
        return smsNotificationService.sendFromTemplate(
                telephone,
                TEMPLATE_VEHICULE_INCORPORE_FLOTTE,
                Map.of(
                        "nombreVehicules", String.valueOf(nombreVehicules),
                        "flotteNom", fleet.getName(),
                        "flotteNumero", fleet.getFleetNumber()
                ),
                TypeMessageSms.vehicule_incorpore_flotte
        );
    }

    /** Point d'entree correct pour VehiculeImportService.confirm() (bug reel trouve en direct le
     * 2026-09-13) : confirm() n'est pas @Transactional (delibere, cf. son propre javadoc) et le Fleet
     * qu'il charge en debut de methode est donc detache par le moment ou cette methode est appelee en
     * fin de traitement - fleet.getClient() (lazy) levait LazyInitializationException des qu'une
     * flotte sans managerPhone forcait le repli sur le telephone du client. Recharge le Fleet a neuf
     * PUIS accede a son client, le tout dans UNE SEULE transaction active. */
    @Transactional(readOnly = true)
    public Boolean notifierIncorporationApresImport(Long fleetId, int nombreVehicules) {
        return notifierIncorporation(resolveFleet(fleetId), nombreVehicules);
    }

    /** Ajout unitaire (addVehicle uniquement, pas l'import en masse) - message detaille avec le type
     * (moto/vehicule), la marque/modele, et le n de police du contrat le plus recent deja existant
     * pour ce vehicule s'il y en a un (aucun contrat n'est cree ni requis a l'incorporation - simple
     * lecture, cf. decision du 2026-09-13 : pas de champ police en texte libre, le contrat reel reste
     * l'unique source de verite). Meme convention de retour (null/true/false) que notifierIncorporation. */
    Boolean notifierIncorporationUnitaire(Fleet fleet, Vehicule vehicule) {
        String telephone = telephoneGestionnaire(fleet);
        if (!StringUtils.hasText(telephone)) {
            return null;
        }
        String typeLabel = vehicule.getType() == VehiculeType.MOTO ? "moto" : "vehicule";
        String policyNumber = contractRepository.findByVehiculeIdInOrderByCreatedAtDesc(List.of(vehicule.getId())).stream()
                .findFirst().map(Contract::getPolicyNumber).orElse(null);
        String policeSuffix = StringUtils.hasText(policyNumber) ? " - police " + policyNumber : "";

        return smsNotificationService.sendFromTemplate(
                telephone,
                TEMPLATE_VEHICULE_FLOTTE_DETAIL,
                Map.of(
                        "typeVehicule", typeLabel,
                        "marque", vehicule.getMarque(),
                        "modele", vehicule.getModele(),
                        "flotteNom", fleet.getName(),
                        "flotteNumero", fleet.getFleetNumber(),
                        "policeSuffix", policeSuffix
                ),
                TypeMessageSms.vehicule_incorpore_flotte
        );
    }

    private String telephoneGestionnaire(Fleet fleet) {
        return StringUtils.hasText(fleet.getManagerPhone())
                ? fleet.getManagerPhone()
                : (fleet.getClient() == null ? null : fleet.getClient().getClientPhone());
    }

    @Transactional
    public FleetVehicleResponse removeVehicle(Long fleetId, RemoveVehicleRequest request) {
        Fleet fleet = resolveFleet(fleetId);
        FleetVehicle fleetVehicle = fleetVehicleRepository.findByFleetIdAndVehiculeIdAndStatus(fleetId, request.vehiculeId(), FleetVehicleStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicule non actif dans cette flotte: " + request.vehiculeId()));
        Vehicule vehicule = resolveVehicule(request.vehiculeId());

        fleetVehicle.setStatus(FleetVehicleStatus.REMOVED);
        fleetVehicle.setDateRemoved(LocalDate.now());
        fleetVehicleRepository.save(fleetVehicle);

        vehicule.setCurrentFleet(null);
        vehiculeRepository.save(vehicule);
        recomputeCounters(fleet);

        recordMovement(fleet, vehicule, FleetMovementType.REMOVE, request.reason(), null, null, null, null);
        functionalAuditRecorder.recordAction("FLOTTE", "Vehicule", String.valueOf(vehicule.getId()), ActionAudit.REMOVE,
                "Vehicule " + libelleVehicule(vehicule) + " retire de la flotte " + fleet.getFleetNumber());

        return toVehicleResponse(fleetVehicle);
    }

    @Transactional
    public FleetVehicleResponse replaceVehicle(Long fleetId, ReplaceVehicleRequest request) {
        Fleet fleet = resolveFleet(fleetId);
        requireFleetActive(fleet);
        if (request.oldVehiculeId().equals(request.newVehiculeId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible de remplacer un vehicule par lui-meme");
        }

        FleetVehicle oldRow = fleetVehicleRepository.findByFleetIdAndVehiculeIdAndStatus(fleetId, request.oldVehiculeId(), FleetVehicleStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicule non actif dans cette flotte: " + request.oldVehiculeId()));
        Vehicule oldVehicule = resolveVehicule(request.oldVehiculeId());
        Vehicule newVehicule = resolveVehicule(request.newVehiculeId());
        requireVehiculeEligible(newVehicule, fleet);

        oldRow.setStatus(FleetVehicleStatus.REPLACED);
        oldRow.setDateRemoved(LocalDate.now());
        fleetVehicleRepository.save(oldRow);
        oldVehicule.setCurrentFleet(null);
        vehiculeRepository.save(oldVehicule);

        FleetVehicle newRow;
        try {
            newRow = fleetVehicleRepository.saveAndFlush(FleetVehicle.builder()
                    .fleet(fleet)
                    .vehicule(newVehicule)
                    .status(FleetVehicleStatus.ACTIVE)
                    .dateAdded(LocalDate.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nouveau vehicule deja actif dans une flotte (conflit concurrent)");
        }
        newVehicule.setCurrentFleet(fleet);
        vehiculeRepository.save(newVehicule);

        recomputeCounters(fleet);

        recordMovement(fleet, newVehicule, FleetMovementType.REPLACE, request.reason(), oldVehicule, newVehicule, null, null);
        functionalAuditRecorder.recordAction("FLOTTE", "Vehicule", String.valueOf(newVehicule.getId()), ActionAudit.REPLACE,
                "Vehicule " + libelleVehicule(oldVehicule) + " remplace par " + libelleVehicule(newVehicule) + " dans la flotte " + fleet.getFleetNumber());

        return toVehicleResponse(newRow);
    }

    @Transactional
    public FleetVehicleResponse transferVehicle(Long sourceFleetId, Long vehiculeId, TransferVehicleRequest request) {
        Fleet source = resolveFleet(sourceFleetId);
        Fleet destination = resolveFleet(request.destinationFleetId());
        if (sourceFleetId.equals(request.destinationFleetId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfert vers la meme flotte impossible");
        }
        requireFleetActive(destination);
        if (!source.getClient().getId().equals(destination.getClient().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfert interdit entre flottes de clients differents");
        }

        FleetVehicle sourceRow = fleetVehicleRepository.findByFleetIdAndVehiculeIdAndStatus(sourceFleetId, vehiculeId, FleetVehicleStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicule non actif dans la flotte source: " + vehiculeId));
        Vehicule vehicule = resolveVehicule(vehiculeId);
        // Meme regle de coherence type flotte / type vehicule que addVehicle/replaceVehicle
        // (requireVehiculeEligible) - transferVehicle a sa propre logique inline (pas d'appel a
        // requireVehiculeEligible, le vehicule est deja actif ailleurs donc les autres controles ne
        // s'appliquent pas tels quels) et avait ete oublie a l'introduction de la regle le 2026-09-13 :
        // une moto pouvait etre transferee vers une flotte AUTO sans blocage. Bug reel constate en
        // direct le jour meme.
        if (!fleetTypeAccepte(destination.getFleetType(), vehicule.getType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une flotte " + destination.getFleetType() + " n'accepte pas un vehicule de type " + vehicule.getType());
        }

        sourceRow.setStatus(FleetVehicleStatus.TRANSFERRED);
        sourceRow.setDateRemoved(LocalDate.now());
        // saveAndFlush (pas save) : Hibernate ordonne un flush par TYPE d'operation (tous les INSERT
        // avant tous les UPDATE), pas par ordre d'appel. Sans flush explicite ici, l'INSERT de
        // destinationRow ci-dessous (meme vehicule_id) partirait avant que cet UPDATE ne soit
        // physiquement ecrit - la colonne generee active_vehicule_id verrait alors temporairement
        // DEUX lignes actives pour ce vehicule (source pas encore TRANSFERRED en base + destination
        // deja ACTIVE), violant a tort la contrainte unique. Bug reel constate en direct.
        fleetVehicleRepository.saveAndFlush(sourceRow);

        FleetVehicle destinationRow;
        try {
            destinationRow = fleetVehicleRepository.saveAndFlush(FleetVehicle.builder()
                    .fleet(destination)
                    .vehicule(vehicule)
                    .status(FleetVehicleStatus.ACTIVE)
                    .dateAdded(LocalDate.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicule deja actif dans une flotte (conflit concurrent)");
        }
        vehicule.setCurrentFleet(destination);
        vehiculeRepository.save(vehicule);

        recomputeCounters(source);
        recomputeCounters(destination);

        recordMovement(source, vehicule, FleetMovementType.TRANSFER, request.reason(), null, null, source, destination);
        functionalAuditRecorder.recordAction("FLOTTE", "Vehicule", String.valueOf(vehicule.getId()), ActionAudit.TRANSFER,
                "Vehicule " + libelleVehicule(vehicule) + " transfere de " + source.getFleetNumber() + " vers " + destination.getFleetNumber());

        return toVehicleResponse(destinationRow);
    }

    public PageResponse<FleetMovementResponse> listMovements(Long fleetId, FleetMovementType movementType, Long vehicleId,
                                                               LocalDate dateFrom, LocalDate dateTo, Long userId,
                                                               Integer page, Integer size) {
        resolveFleet(fleetId);
        List<FleetMovementResponse> filtered = fleetMovementRepository.findByFleetIdOrderByCreatedAtDesc(fleetId).stream()
                .filter(m -> movementType == null || m.getMovementType() == movementType)
                .filter(m -> vehicleId == null || (m.getVehicule() != null && vehicleId.equals(m.getVehicule().getId())))
                .filter(m -> dateFrom == null || !m.getMovementDate().isBefore(dateFrom))
                .filter(m -> dateTo == null || !m.getMovementDate().isAfter(dateTo))
                .filter(m -> userId == null || (m.getPerformedBy() != null && userId.equals(m.getPerformedBy().getId())))
                .map(this::toMovementResponse)
                .toList();
        return paginate(filtered, page, size);
    }

    public List<FleetContractResponse> listContracts(Long fleetId) {
        resolveFleet(fleetId);
        // fv.getVehicule() peut etre null (pas juste une proxy qui levait avant le 2026-09-13) pour
        // une ligne historique dont le vehicule a depuis ete supprime : @NotFound(IGNORE) sur
        // FleetVehicle.vehicule renvoie desormais null au lieu de lever ObjectNotFoundException -
        // filtrer avant .getId() sous peine de NullPointerException (bug reel constate en direct par
        // le frontend le jour meme, regression introduite par ce meme correctif).
        List<Long> vehiculeIds = fleetVehicleRepository.findByFleetIdOrderByCreatedAtDesc(fleetId).stream()
                .map(FleetVehicle::getVehicule)
                .filter(java.util.Objects::nonNull)
                .map(Vehicule::getId)
                .distinct()
                .toList();
        if (vehiculeIds.isEmpty()) {
            return List.of();
        }
        return contractRepository.findByVehiculeIdInOrderByCreatedAtDesc(vehiculeIds).stream()
                .map(this::toContractResponse)
                .toList();
    }

    /**
     * Rattache un contrat mono existant (souscrit hors flotte, sans vehiculeId) a une flotte, en le
     * liant a un vehicule (2026-09-14, gap remonte par le frontend : jusqu'ici le seul chemin qui
     * liait un contrat a une flotte etait la creation d'un NOUVEAU contrat depuis l'onglet Vehicules
     * d'une flotte). Si le vehicule n'est pas deja membre de la flotte, il y est ajoute dans la meme
     * operation (reutilise ajouterVehiculeAuCoeur, donc les memes garde-fous que POST
     * /fleets/{fleetId}/vehicles : flotte active, vehicule ACTIF, pas deja actif ailleurs, coherence
     * FleetType/VehiculeType). Deux garde-fous supplementaires cote
     * contrat, absents du PUT /contracts/{id} general (qui accepte vehiculeId sans aucun controle) :
     * le contrat ne doit pas deja avoir de vehicule (remplacer un vehicule existant n'est pas ce
     * flux-la) et le vehicule ne doit pas deja etre rattache a un AUTRE contrat non annule.
     */
    @Transactional
    public FleetContractResponse attachContract(Long fleetId, AttachContractRequest request) {
        Fleet fleet = resolveFleet(fleetId);
        Contract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable: " + request.contractId()));
        if (contract.getVehicule() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Contrat " + contract.getPolicyNumber() + " deja rattache a un vehicule (id " + contract.getVehicule().getId() + ")");
        }

        Vehicule vehicule = resolveVehicule(request.vehiculeId());
        if (!vehicule.getProprietaire().getId().equals(contract.getInsured().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le proprietaire du vehicule ne correspond pas a l'assure du contrat");
        }
        contractRepository.findByVehiculeIdInOrderByCreatedAtDesc(List.of(vehicule.getId())).stream()
                .filter(c -> !c.getId().equals(contract.getId()) && c.getStatus() != ci.assurzen.entities.ContractStatus.cancelled)
                .findFirst()
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Vehicule deja rattache au contrat " + c.getPolicyNumber() + " (id " + c.getId() + ")");
                });

        if (vehicule.getCurrentFleet() == null) {
            ajouterVehiculeAuCoeur(fleet, vehicule, request.reason());
            recomputeCounters(fleet);
        } else if (!vehicule.getCurrentFleet().getId().equals(fleetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicule deja actif dans une autre flotte (id " + vehicule.getCurrentFleet().getId() + ")");
        }

        contract.setVehicule(vehicule);
        Contract saved = contractRepository.save(contract);
        functionalAuditRecorder.recordAction("FLOTTE", "Contract", String.valueOf(saved.getId()), ActionAudit.ADD,
                "Contrat " + saved.getPolicyNumber() + " rattache au vehicule " + libelleVehicule(vehicule) + " de la flotte " + fleet.getFleetNumber());

        return toContractResponse(saved);
    }

    /** Avis d'echeance (avis_echeance/fin_contrat, meme cycle qu'un contrat "simple" depuis le
     * correctif ContractsService.create() du 2026-09-13) pour les vehicules/motos ACTUELLEMENT
     * incorpores dans la flotte (status ACTIVE uniquement - contrairement a listContracts() qui
     * couvre aussi l'historique des vehicules retires/transferes). Un contrat sans dateEcheance ou
     * sans contrat du tout pour un vehicule incorpore n'apparait simplement pas (rien a rappeler). */
    public List<FleetEcheanceResponse> listEcheances(Long fleetId) {
        resolveFleet(fleetId);
        // Meme garde-fou que listContracts() ci-dessus (fv.getVehicule() peut etre null depuis
        // @NotFound(IGNORE)) - non atteignable en theorie ici puisqu'on ne regarde que le statut
        // ACTIVE et que VehiculeService.delete() bloque la suppression d'un vehicule encore actif
        // dans une flotte, mais filtre quand meme par defense en profondeur.
        List<Long> vehiculeIdsActifs = fleetVehicleRepository.findByFleetIdAndStatusOrderByCreatedAtDesc(fleetId, FleetVehicleStatus.ACTIVE).stream()
                .map(FleetVehicle::getVehicule)
                .filter(java.util.Objects::nonNull)
                .map(Vehicule::getId)
                .distinct()
                .toList();
        if (vehiculeIdsActifs.isEmpty()) {
            return List.of();
        }
        List<Contract> contrats = contractRepository.findByVehiculeIdInOrderByCreatedAtDesc(vehiculeIdsActifs);
        if (contrats.isEmpty()) {
            return List.of();
        }
        List<Long> contractIds = contrats.stream().map(Contract::getId).toList();
        Map<Long, List<NotificateurSmsResponse>> rappelsParContrat = smsNotificationService.getMessagesForContracts(contractIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(NotificateurSmsResponse::contractId));

        return contrats.stream()
                .map(contrat -> toEcheanceResponse(contrat, rappelsParContrat.getOrDefault(contrat.getId(), List.of())))
                .toList();
    }

    // ---------------------------------------------------------------- Helpers

    Fleet resolveFleet(Long id) {
        return fleetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flotte introuvable: " + id));
    }

    /** PAS de verrou pessimiste ici (delibere) : ce serveur est en realite MariaDB (bien que le
     * dialecte Hibernate configure globalement pour toute l'application soit MySQLDialect) -
     * confirme en direct, toute requete verrouillee (JPQL @Lock ou EntityManager.find(..., lockMode))
     * fait emettre "for update of &lt;alias&gt;" par ce dialecte, syntaxe que MariaDB rejette
     * (erreur SQL), quelle que soit la forme de la requete. Corriger le dialecte global sortirait du
     * perimetre de ce module (impact sur toute l'application) - a signaler separement. La garantie
     * de concurrence reste correcte sans lock : la contrainte DB unique sur
     * t_assur_zen_fleet_vehicles (active_vehicule_id, cf. sql/manual/) est la veritable autorite -
     * deux ajouts concurrents du meme vehicule se soldent par un seul INSERT reussi, l'autre
     * echouant en DataIntegrityViolationException -> 409, avant meme de toucher Vehicule.currentFleet. */
    Vehicule resolveVehicule(Long id) {
        return vehiculeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicule introuvable: " + id));
    }

    private Insured resolveInsured(Long insuredId) {
        return insuredRepository.findById(insuredId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable: " + insuredId));
    }

    private Produit resolveProduit(Long produitId) {
        return produitRepository.findById(produitId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produit introuvable: " + produitId));
    }

    void requireFleetActive(Fleet fleet) {
        if (fleet.getStatus() != FleetStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flotte non active (statut: " + fleet.getStatus() + ")");
        }
    }

    /** Verifie qu'un vehicule peut rejoindre une flotte : statut ACTIF, pas deja actif ailleurs, et
     * type compatible. Le proprietaire du vehicule n'a PAS a etre le client de la flotte (regle metier
     * du 2026-09-24 : controle "proprietaire = client de la flotte" retire). currentFleet reste un cache de lecture - la
     * garantie d'unicite reelle vient de la contrainte DB sur t_assur_zen_fleet_vehicles (cf.
     * resolveVehicule et sql/manual/), pas de cette verification applicative seule. */
    private void requireVehiculeEligible(Vehicule vehicule, Fleet fleet) {
        if (vehicule.getStatut() != VehiculeStatut.ACTIF) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicule non eligible (statut: " + vehicule.getStatut() + ")");
        }
        if (vehicule.getCurrentFleet() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicule deja actif dans une flotte (id " + vehicule.getCurrentFleet().getId() + ")");
        }
        if (!fleetTypeAccepte(fleet.getFleetType(), vehicule.getType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Une flotte " + fleet.getFleetType() + " n'accepte pas un vehicule de type " + vehicule.getType());
        }
    }

    /** Regle de coherence type flotte / type vehicule (2026-09-13) : AUTO n'accepte que AUTO, MOTO
     * n'accepte que MOTO, MIXED accepte les deux - non verifie avant cette date (une flotte AUTO
     * pouvait silencieusement recevoir une moto et inversement). */
    private boolean fleetTypeAccepte(FleetType fleetType, VehiculeType vehiculeType) {
        return switch (fleetType) {
            case AUTO -> vehiculeType == VehiculeType.AUTO;
            case MOTO -> vehiculeType == VehiculeType.MOTO;
            case MIXED -> true;
        };
    }

    /** Libelle lisible pour les messages d'audit (2026-09-13) : immatriculation devenue optionnelle,
     * repli sur le numero de chassis puis sur l'id plutot que d'afficher litteralement "null". */
    private String libelleVehicule(Vehicule vehicule) {
        if (StringUtils.hasText(vehicule.getImmatriculation())) {
            return vehicule.getImmatriculation();
        }
        if (StringUtils.hasText(vehicule.getNumeroChassis())) {
            return "chassis " + vehicule.getNumeroChassis();
        }
        return "id " + vehicule.getId();
    }

    /** Recalcule totalVehicles/activeVehicles par requete COUNT, jamais par increment en memoire -
     * evite la derive de compteur deja rencontree dans ce projet (Prime Objectif, Dynamic
     * Configuration). Appele dans la meme transaction que le mouvement qui declenche le recalcul. */
    void recomputeCounters(Fleet fleet) {
        long total = fleetVehicleRepository.countDistinctVehiculeIdByFleetId(fleet.getId());
        long active = fleetVehicleRepository.countByFleetIdAndStatus(fleet.getId(), FleetVehicleStatus.ACTIVE);
        fleet.setTotalVehicles((int) total);
        fleet.setActiveVehicles((int) active);
        fleetRepository.save(fleet);
    }

    private void recordMovement(Fleet fleet, Vehicule vehicule, FleetMovementType type, String reason,
                                 Vehicule oldVehicule, Vehicule newVehicule, Fleet sourceFleet, Fleet destinationFleet) {
        fleetMovementRepository.save(FleetMovement.builder()
                .fleet(fleet)
                .vehicule(vehicule)
                .movementType(type)
                .movementDate(LocalDate.now())
                .reason(reason)
                .oldVehicule(oldVehicule)
                .newVehicule(newVehicule)
                .sourceFleet(sourceFleet)
                .destinationFleet(destinationFleet)
                .contract(bestEffortContract(vehicule))
                .performedBy(currentUser())
                .build());
    }

    private Contract bestEffortContract(Vehicule vehicule) {
        return contractRepository.findByVehiculeIdInOrderByCreatedAtDesc(List.of(vehicule.getId())).stream()
                .findFirst()
                .orElse(null);
    }

    private User currentUser() {
        AuthenticatedUserPrincipal principal = authService.currentPrincipal();
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable: " + principal.userId()));
    }

    String displayName(User user) {
        if (user == null) {
            return null;
        }
        return profileRepository.findByUserId(user.getId())
                .map(profile -> profile.getFirstname() + " " + profile.getLastname())
                .orElseGet(() -> safe(user::getUsername));
    }

    private List<FleetResponse> filter(String fleetNumber, Long clientId, String name, FleetType fleetType,
                                        FleetStatus status, LocalDate startDate, LocalDate endDate) {
        String normalizedNumber = StringUtils.hasText(fleetNumber) ? fleetNumber.trim().toUpperCase(Locale.ROOT) : null;
        String normalizedName = StringUtils.hasText(name) ? name.trim().toLowerCase(Locale.ROOT) : null;

        return fleetRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(fleet -> normalizedNumber == null || fleet.getFleetNumber().toUpperCase(Locale.ROOT).contains(normalizedNumber))
                .filter(fleet -> clientId == null || (fleet.getClient() != null && clientId.equals(fleet.getClient().getId())))
                .filter(fleet -> normalizedName == null || fleet.getName().toLowerCase(Locale.ROOT).contains(normalizedName))
                .filter(fleet -> fleetType == null || fleet.getFleetType() == fleetType)
                .filter(fleet -> status == null || fleet.getStatus() == status)
                .filter(fleet -> startDate == null || (fleet.getStartDate() != null && !fleet.getStartDate().isBefore(startDate)))
                .filter(fleet -> endDate == null || (fleet.getEndDate() != null && !fleet.getEndDate().isAfter(endDate)))
                .map(this::toResponse)
                .toList();
    }

    private <T> PageResponse<T> paginate(List<T> items, Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        int totalElements = items.size();
        int totalPages = (int) Math.ceil(totalElements / (double) pageSize);
        int fromIndex = Math.min(pageNumber * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);

        return new PageResponse<>(items.subList(fromIndex, toIndex), pageNumber, pageSize, totalElements, totalPages);
    }

    private FleetResponse toResponse(Fleet fleet) {
        return new FleetResponse(
                fleet.getId(),
                fleet.getFleetNumber(),
                fleet.getClient() == null ? null : fleet.getClient().getId(),
                fleet.getClient() == null ? null : fleet.getClient().getPrenoms() + " " + fleet.getClient().getNom(),
                fleet.getName(),
                fleet.getDescription(),
                fleet.getFleetType(),
                fleet.getProduit() == null ? null : safe(fleet.getProduit()::getId),
                fleet.getProduit() == null ? null : safe(fleet.getProduit()::getNomProduit),
                fleet.getStatus(),
                fleet.getStartDate(),
                fleet.getEndDate(),
                fleet.getManagerName(),
                fleet.getManagerPhone(),
                fleet.getManagerEmail(),
                fleet.getTotalVehicles(),
                fleet.getActiveVehicles(),
                fleet.getCreatedAt(),
                fleet.getUpdatedAt()
        );
    }

    private FleetVehicleResponse toVehicleResponse(FleetVehicle fleetVehicle) {
        return toVehicleResponse(fleetVehicle, null);
    }

    private FleetVehicleResponse toVehicleResponse(FleetVehicle fleetVehicle, Boolean notificationEnvoyee) {
        return new FleetVehicleResponse(
                fleetVehicle.getId(),
                fleetVehicle.getFleet() == null ? null : fleetVehicle.getFleet().getId(),
                fleetVehicle.getFleet() == null ? null : fleetVehicle.getFleet().getFleetNumber(),
                fleetVehicle.getVehicule() == null ? null : fleetVehicle.getVehicule().getId(),
                fleetVehicle.getVehicule() == null ? null : fleetVehicle.getVehicule().getImmatriculation(),
                fleetVehicle.getVehicule() == null ? null : fleetVehicle.getVehicule().getType(),
                fleetVehicle.getStatus(),
                fleetVehicle.getDateAdded(),
                fleetVehicle.getDateRemoved(),
                notificationEnvoyee
        );
    }

    private FleetMovementResponse toMovementResponse(FleetMovement movement) {
        return new FleetMovementResponse(
                movement.getId(),
                movement.getFleet() == null ? null : movement.getFleet().getId(),
                movement.getVehicule() == null ? null : movement.getVehicule().getId(),
                movement.getVehicule() == null ? null : movement.getVehicule().getImmatriculation(),
                movement.getMovementType(),
                movement.getMovementDate(),
                movement.getReason(),
                movement.getOldVehicule() == null ? null : movement.getOldVehicule().getId(),
                movement.getOldVehicule() == null ? null : movement.getOldVehicule().getImmatriculation(),
                movement.getNewVehicule() == null ? null : movement.getNewVehicule().getId(),
                movement.getNewVehicule() == null ? null : movement.getNewVehicule().getImmatriculation(),
                movement.getSourceFleet() == null ? null : movement.getSourceFleet().getId(),
                movement.getSourceFleet() == null ? null : movement.getSourceFleet().getFleetNumber(),
                movement.getDestinationFleet() == null ? null : movement.getDestinationFleet().getId(),
                movement.getDestinationFleet() == null ? null : movement.getDestinationFleet().getFleetNumber(),
                movement.getContract() == null ? null : movement.getContract().getId(),
                movement.getContract() == null ? null : movement.getContract().getPolicyNumber(),
                movement.getPerformedBy() == null ? null : movement.getPerformedBy().getId(),
                displayName(movement.getPerformedBy()),
                movement.getCreatedAt()
        );
    }

    private FleetContractResponse toContractResponse(Contract contract) {
        return new FleetContractResponse(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getProduct() == null ? null : safe(contract.getProduct()::getNomProduit),
                contract.getVehicule() == null ? null : contract.getVehicule().getId(),
                contract.getVehicule() == null ? null : contract.getVehicule().getImmatriculation(),
                contract.getDateEffet(),
                contract.getDateEcheance(),
                contract.getPremium(),
                contract.getStatus()
        );
    }

    private FleetEcheanceResponse toEcheanceResponse(Contract contract, List<NotificateurSmsResponse> rappels) {
        Vehicule vehicule = contract.getVehicule();
        return new FleetEcheanceResponse(
                contract.getId(),
                contract.getPolicyNumber(),
                contract.getStatus(),
                vehicule == null ? null : vehicule.getId(),
                vehicule == null ? null : vehicule.getImmatriculation(),
                vehicule == null ? null : vehicule.getMarque(),
                vehicule == null ? null : vehicule.getModele(),
                vehicule == null ? null : vehicule.getType(),
                contract.getDateEffet(),
                contract.getDateEcheance(),
                rappels
        );
    }
}
