package ci.assurzen.services;

import ci.assurzen.dto.VehiculeRequest;
import ci.assurzen.dto.VehiculeResponse;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.entities.Fleet;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.InsuredRepository;
import ci.assurzen.repositories.VehiculeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VehiculeServiceTest {

    private VehiculeRepository vehiculeRepository;
    private InsuredRepository insuredRepository;
    private ContractRepository contractRepository;
    private VehiculeService service;

    private void setUp() {
        vehiculeRepository = mock(VehiculeRepository.class);
        insuredRepository = mock(InsuredRepository.class);
        contractRepository = mock(ContractRepository.class);
        service = new VehiculeService(vehiculeRepository, insuredRepository, contractRepository);
    }

    private VehiculeRequest request(String immatriculation, Long proprietaireId, VehiculeStatut statut) {
        return new VehiculeRequest(immatriculation, VehiculeType.AUTO, "Toyota", "Corolla", null, null, null, null, null, proprietaireId, statut);
    }

    private VehiculeRequest requestMoto(String immatriculation, Integer cylindree, Long proprietaireId) {
        return new VehiculeRequest(immatriculation, VehiculeType.MOTO, "Yamaha", "Crypton", "CH-001", null, null, cylindree, null, proprietaireId, null);
    }

    @Test
    void creerVehicule_immatriculationDejaUtilisee_estRejeteAvec409() {
        setUp();
        when(vehiculeRepository.findByImmatriculation("AB123CD")).thenReturn(Optional.of(mock(Vehicule.class)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("ab123cd", 1L, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void creerVehicule_proprietaireInexistant_renvoie404() {
        setUp();
        when(vehiculeRepository.findByImmatriculation("AB123CD")).thenReturn(Optional.empty());
        when(insuredRepository.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.create(request("AB123CD", 1L, null)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void creerVehicule_casNominal_statutActifParDefaut() {
        setUp();
        Insured proprietaire = Insured.builder().id(1L).nom("Client").prenoms("Test").build();
        when(vehiculeRepository.findByImmatriculation("AB123CD")).thenReturn(Optional.empty());
        when(insuredRepository.findById(1L)).thenReturn(Optional.of(proprietaire));
        when(vehiculeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VehiculeResponse response = service.create(request("AB123CD", 1L, null));

        assertEquals(VehiculeStatut.ACTIF, response.statut());
        assertEquals("AB123CD", response.immatriculation());
    }

    @Test
    void creerMoto_sansImmatriculation_neDeclenchePasDeVerificationDeDoublon() {
        // Immatriculation optionnelle (2026-09-13) - findByImmatriculation ne doit meme pas etre
        // appele quand elle est absente (WHERE immatriculation IS NULL matcherait n'importe quel
        // autre vehicule sans plaque, un faux-positif de doublon serait un vrai bug).
        setUp();
        Insured proprietaire = Insured.builder().id(1L).nom("Client").prenoms("Test").build();
        when(insuredRepository.findById(1L)).thenReturn(Optional.of(proprietaire));
        when(vehiculeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VehiculeResponse response = service.create(requestMoto(null, 110, 1L));

        verify(vehiculeRepository, never()).findByImmatriculation(any());
        assertEquals(null, response.immatriculation());
        assertEquals(110, response.cylindree());
        assertEquals(VehiculeType.MOTO, response.type());
    }

    @Test
    void creerMoto_avecCylindree_estPersisteeEtRenvoyeeDansLaReponse() {
        setUp();
        Insured proprietaire = Insured.builder().id(1L).nom("Client").prenoms("Test").build();
        when(vehiculeRepository.findByImmatriculation("MOTO123")).thenReturn(Optional.empty());
        when(insuredRepository.findById(1L)).thenReturn(Optional.of(proprietaire));
        when(vehiculeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VehiculeResponse response = service.create(requestMoto("MOTO123", 125, 1L));

        assertEquals(125, response.cylindree());
    }

    @Test
    void supprimerVehicule_rattacheAUneFlotte_estRejeteAvec409() {
        setUp();
        Vehicule vehicule = Vehicule.builder().id(1L).currentFleet(Fleet.builder().id(9L).build()).build();
        when(vehiculeRepository.findById(1L)).thenReturn(Optional.of(vehicule));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(vehiculeRepository, never()).save(any());
    }

    @Test
    void supprimerVehicule_associeAUnContrat_estRejeteAvec409() {
        setUp();
        Vehicule vehicule = Vehicule.builder().id(1L).currentFleet(null).build();
        when(vehiculeRepository.findById(1L)).thenReturn(Optional.of(vehicule));
        when(contractRepository.existsByVehiculeId(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(vehiculeRepository, never()).save(any());
    }

    @Test
    void supprimerVehicule_libreDeFlotteEtDeContrat_estUneSuppressionLogique() {
        setUp();
        Vehicule vehicule = Vehicule.builder().id(1L).currentFleet(null).build();
        when(vehiculeRepository.findById(1L)).thenReturn(Optional.of(vehicule));
        when(contractRepository.existsByVehiculeId(1L)).thenReturn(false);

        service.delete(1L);

        assertEquals(true, vehicule.isDeleted());
        verify(vehiculeRepository).save(vehicule);
    }
}
