package ci.assurzen.services;

import ci.assurzen.entities.Fleet;
import ci.assurzen.entities.FleetVehicle;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Vehicule;
import ci.assurzen.entities.VehiculeStatut;
import ci.assurzen.entities.VehiculeType;
import ci.assurzen.repositories.VehiculeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Couvre la creation-ou-reutilisation du Vehicule lors d'une ligne d'import (le reste - eligibilite,
 * liaison a la flotte, mouvement, audit - est deja couvert par FleetServiceTest via
 * ajouterVehiculeAuCoeur, reutilise tel quel ici). */
class VehiculeImportRowProcessorTest {

    private record Fixture(VehiculeImportRowProcessor processor, FleetService fleetService, VehiculeRepository vehiculeRepository) {
    }

    private Fixture newFixture() {
        FleetService fleetService = mock(FleetService.class);
        VehiculeRepository vehiculeRepository = mock(VehiculeRepository.class);
        return new Fixture(new VehiculeImportRowProcessor(fleetService, vehiculeRepository), fleetService, vehiculeRepository);
    }

    private LigneVehiculeValidee ligne(String immatriculation) {
        return new LigneVehiculeValidee(2, immatriculation, VehiculeType.AUTO, "Toyota", "Corolla", null, null, null, null, null, VehiculeStatut.ACTIF);
    }

    @Test
    void importerLigne_vehiculeExistant_estReutiliseSansEtreRecree() {
        Fixture fx = newFixture();
        Insured client = Insured.builder().id(1L).build();
        Fleet fleet = Fleet.builder().id(1L).client(client).build();
        Vehicule existant = Vehicule.builder().id(5L).immatriculation("CI-1234-AA").build();
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.vehiculeRepository().findByImmatriculation("CI-1234-AA")).thenReturn(Optional.of(existant));
        when(fx.fleetService().ajouterVehiculeAuCoeur(any(), any(), any())).thenReturn(mock(FleetVehicle.class));

        fx.processor().importerLigne(1L, ligne("CI-1234-AA"));

        verify(fx.vehiculeRepository(), org.mockito.Mockito.never()).save(any());
        ArgumentCaptor<Vehicule> captor = ArgumentCaptor.forClass(Vehicule.class);
        verify(fx.fleetService()).ajouterVehiculeAuCoeur(any(), captor.capture(), any());
        assertEquals(5L, captor.getValue().getId());
    }

    @Test
    void importerLigne_vehiculeInexistant_estCreeAvecLeClientDeLaFlotteCommeProprietaire() {
        Fixture fx = newFixture();
        Insured client = Insured.builder().id(1L).build();
        Fleet fleet = Fleet.builder().id(1L).client(client).build();
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.vehiculeRepository().findByImmatriculation("CI-1234-AA")).thenReturn(Optional.empty());
        when(fx.vehiculeRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.fleetService().ajouterVehiculeAuCoeur(any(), any(), any())).thenReturn(mock(FleetVehicle.class));

        fx.processor().importerLigne(1L, ligne("CI-1234-AA"));

        ArgumentCaptor<Vehicule> captor = ArgumentCaptor.forClass(Vehicule.class);
        verify(fx.vehiculeRepository()).save(captor.capture());
        assertEquals(client, captor.getValue().getProprietaire());
        assertEquals("CI-1234-AA", captor.getValue().getImmatriculation());
        assertEquals(VehiculeStatut.ACTIF, captor.getValue().getStatut());
    }

    @Test
    void importerLigne_motoSansImmatriculation_chercheParChassisPasParFindByImmatriculationNull() {
        // Garde-fou (2026-09-13) : findByImmatriculation(null) matcherait N'IMPORTE QUEL vehicule sans
        // plaque en base (WHERE immatriculation IS NULL), pas forcement celui de cette ligne - le
        // repli doit passer par le numero de chassis, jamais par un appel findByImmatriculation(null).
        Fixture fx = newFixture();
        Insured client = Insured.builder().id(1L).build();
        Fleet fleet = Fleet.builder().id(1L).client(client).build();
        Vehicule existant = Vehicule.builder().id(9L).numeroChassis("CH-MOTO-001").build();
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.vehiculeRepository().findByNumeroChassis("CH-MOTO-001")).thenReturn(Optional.of(existant));
        when(fx.fleetService().ajouterVehiculeAuCoeur(any(), any(), any())).thenReturn(mock(FleetVehicle.class));

        LigneVehiculeValidee ligneMoto = new LigneVehiculeValidee(3, null, VehiculeType.MOTO, "Yamaha", "Crypton",
                "CH-MOTO-001", null, null, 110, null, VehiculeStatut.ACTIF);
        fx.processor().importerLigne(1L, ligneMoto);

        verify(fx.vehiculeRepository(), org.mockito.Mockito.never()).findByImmatriculation(any());
        verify(fx.vehiculeRepository(), org.mockito.Mockito.never()).save(any());
        ArgumentCaptor<Vehicule> captor = ArgumentCaptor.forClass(Vehicule.class);
        verify(fx.fleetService()).ajouterVehiculeAuCoeur(any(), captor.capture(), any());
        assertEquals(9L, captor.getValue().getId());
    }

    @Test
    void importerLigne_motoSansImmatriculationEtSansChassisExistant_estCreee() {
        Fixture fx = newFixture();
        Insured client = Insured.builder().id(1L).build();
        Fleet fleet = Fleet.builder().id(1L).client(client).build();
        when(fx.fleetService().resolveFleet(1L)).thenReturn(fleet);
        when(fx.vehiculeRepository().findByNumeroChassis("CH-MOTO-002")).thenReturn(Optional.empty());
        when(fx.vehiculeRepository().save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fx.fleetService().ajouterVehiculeAuCoeur(any(), any(), any())).thenReturn(mock(FleetVehicle.class));

        LigneVehiculeValidee ligneMoto = new LigneVehiculeValidee(4, null, VehiculeType.MOTO, "Yamaha", "Crypton",
                "CH-MOTO-002", null, null, 110, null, VehiculeStatut.ACTIF);
        fx.processor().importerLigne(1L, ligneMoto);

        ArgumentCaptor<Vehicule> captor = ArgumentCaptor.forClass(Vehicule.class);
        verify(fx.vehiculeRepository()).save(captor.capture());
        assertEquals(null, captor.getValue().getImmatriculation());
        assertEquals(110, captor.getValue().getCylindree());
        assertEquals(VehiculeType.MOTO, captor.getValue().getType());
    }
}
