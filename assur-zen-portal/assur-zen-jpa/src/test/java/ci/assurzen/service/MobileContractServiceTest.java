package ci.assurzen.service;

import ci.assurzen.dto.MobileContractDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Produit;
import ci.assurzen.exception.ContractNotFoundException;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.services.DynamicConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lecture des contrats par l'assure connecte (lot b mobile, 2026-11-02). */
class MobileContractServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now(ZoneId.of("Africa/Abidjan"));
    private ClientAccountInsuredRepository liens;
    private ContractRepository contrats;
    private ci.assurzen.repositories.ContractCustomerRequestRepository requests;
    private MobileContractService service;

    @BeforeEach
    void setUp() {
        liens = mock(ClientAccountInsuredRepository.class);
        contrats = mock(ContractRepository.class);
        requests = mock(ci.assurzen.repositories.ContractCustomerRequestRepository.class);
        DynamicConfigurationService config = mock(DynamicConfigurationService.class);
        when(config.getLong(eq("MOBILE_RENEWAL_WINDOW_DAYS"), anyLong())).thenReturn(30L);
        when(liens.findByUserId(45L)).thenReturn(List.of(new ClientAccountInsured(45L, 627L)));
        when(contrats.findByContratPrecedentId(anyLong())).thenReturn(Optional.empty());
        service = new MobileContractService(liens, contrats, config, requests, mock(ci.assurzen.services.TelegramNotificationService.class), mock(MobileProductContentService.class));
    }

    private Contract contrat(long id, ContractStatus statut, LocalDate echeance, String produit) {
        Contract c = new Contract();
        c.setId(id);
        c.setPolicyNumber("P-" + id);
        c.setStatus(statut);
        c.setDateEffet(echeance.minusYears(1));
        c.setDateEcheance(echeance);
        c.setPremium(85000L);
        c.setDuree("12");
        Produit p = new Produit();
        p.setNomProduit(produit);
        c.setProduct(p);
        Insured assure = new Insured();
        assure.setId(627L);
        assure.setNom("TEST");
        assure.setPrenoms("Mobile Demo");
        c.setInsured(assure);
        return c;
    }

    @Test
    void statuts_sontMappesSurLesCinqCodes() {
        assertEquals("ACTIVE", MobileContractService.statut(contrat(1, ContractStatus.signed, AUJOURDHUI.plusMonths(6), "Assurance Auto"), AUJOURDHUI));
        assertEquals("PENDING", MobileContractService.statut(contrat(2, ContractStatus.pending, AUJOURDHUI.plusMonths(6), "Assurance Auto"), AUJOURDHUI));
        assertEquals("TERMINATED", MobileContractService.statut(contrat(3, ContractStatus.cancelled, AUJOURDHUI.plusMonths(6), "Assurance Auto"), AUJOURDHUI));
        assertEquals("EXPIRED", MobileContractService.statut(contrat(4, ContractStatus.signed, AUJOURDHUI.minusDays(1), "Assurance Auto"), AUJOURDHUI));
    }

    @Test
    void identifiantProduit_estUnSlugStable() {
        assertEquals("auto", MobileContractService.identifiantProduit(contrat(1, ContractStatus.signed, AUJOURDHUI, "Assurance Auto")));
        assertEquals("sante", MobileContractService.identifiantProduit(contrat(1, ContractStatus.signed, AUJOURDHUI, "Assurance Santé")));
        assertEquals("automobile-vtc", MobileContractService.identifiantProduit(contrat(1, ContractStatus.signed, AUJOURDHUI, "Assurance Automobile VTC")));
    }

    @Test
    void liste_filtreLesBrouillonsEtCalculeLeRenouvellement() {
        Contract proche = contrat(10, ContractStatus.signed, AUJOURDHUI.plusDays(12), "Assurance Moto");
        Contract lointain = contrat(11, ContractStatus.signed, AUJOURDHUI.plusMonths(7), "Assurance Auto");
        Contract brouillon = contrat(12, ContractStatus.draft, AUJOURDHUI.plusDays(5), "Assurance Auto");
        when(contrats.findByInsuredIdIn(List.of(627L))).thenReturn(List.of(lointain, brouillon, proche));

        List<MobileContractDtos.ContractListItem> items = service.lister(45L, null).items();

        assertEquals(2, items.size());
        assertEquals("10", items.get(0).id());
        assertTrue(items.get(0).renewal().eligible());
        assertEquals(12L, items.get(0).renewal().daysLeft());
        assertFalse(items.get(1).renewal().eligible());
    }

    @Test
    void liste_filtreParStatut() {
        Contract actif = contrat(10, ContractStatus.signed, AUJOURDHUI.plusMonths(6), "Assurance Auto");
        Contract attente = contrat(11, ContractStatus.pending, AUJOURDHUI.plusMonths(6), "Assurance Sante");
        when(contrats.findByInsuredIdIn(any())).thenReturn(List.of(actif, attente));
        assertEquals(1, service.lister(45L, "pending").items().size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.lister(45L, "BIDON"));
    }

    @Test
    void clientSansAssureLie_aUneListeVide() {
        when(liens.findByUserId(99L)).thenReturn(List.of());
        assertTrue(service.lister(99L, null).items().isEmpty());
    }

    @Test
    void detail_contratProche_porteLOffreDeRenouvellementEtLesActions() {
        Contract proche = contrat(10, ContractStatus.signed, AUJOURDHUI.plusDays(12), "Assurance Moto");
        when(contrats.findByIdAndInsuredIdIn(10L, List.of(627L))).thenReturn(Optional.of(proche));

        MobileContractDtos.ContractDetail detail = service.detail(45L, 10L);

        assertNotNull(detail.renewal());
        assertEquals(85000L, detail.renewal().amount());
        assertEquals(12, detail.renewal().durationMonths());
        assertTrue(detail.actions().canDeclareClaim());
        assertEquals("Mobile Demo TEST", detail.holderName());
        assertTrue(detail.documents().isEmpty());
        assertNull(detail.attestation());
    }

    @Test
    void detail_contratEnAttente_nOffrePasDeRenouvellementNiDActions() {
        Contract attente = contrat(11, ContractStatus.pending, AUJOURDHUI.plusDays(12), "Assurance Sante");
        when(contrats.findByIdAndInsuredIdIn(11L, List.of(627L))).thenReturn(Optional.of(attente));
        MobileContractDtos.ContractDetail detail = service.detail(45L, 11L);
        assertNull(detail.renewal());
        assertFalse(detail.actions().canDeclareClaim());
    }

    @Test
    void demande_surContratActif_creeUneReferenceEtEstIdempotente() {
        Contract actif = contrat(10, ContractStatus.signed, AUJOURDHUI.plusMonths(6), "Assurance Auto");
        when(contrats.findByIdAndInsuredIdIn(10L, List.of(627L))).thenReturn(Optional.of(actif));
        when(requests.saveAndFlush(any())).thenAnswer(inv -> {
            ci.assurzen.entities.ContractCustomerRequest r = inv.getArgument(0);
            r.setId(7L);
            return r;
        });
        var cree = service.demander(45L, 10L, "TERMINATION", "Je vends mon vehicule");
        assertTrue(cree.reference().startsWith("RQ-"));
        assertEquals(24, cree.expectedWithinHours());

        ci.assurzen.entities.ContractCustomerRequest ouverte = new ci.assurzen.entities.ContractCustomerRequest();
        ouverte.setReference("RQ-2026-000007");
        when(requests.findFirstByContractIdAndTypeAndStatutOrderByCreatedAtDesc(10L, "TERMINATION", "EN_ATTENTE"))
                .thenReturn(Optional.of(ouverte));
        assertEquals("RQ-2026-000007", service.demander(45L, 10L, "TERMINATION", null).reference());
    }

    @Test
    void demande_surContratNonActif_donne409() {
        Contract attente = contrat(11, ContractStatus.pending, AUJOURDHUI.plusMonths(6), "Assurance Sante");
        when(contrats.findByIdAndInsuredIdIn(11L, List.of(627L))).thenReturn(Optional.of(attente));
        assertThrows(ci.assurzen.exception.ContractNotActiveException.class, () -> service.demander(45L, 11L, "MODIFICATION", null));
    }

    @Test
    void demande_surContratD_unTiers_donne404() {
        when(contrats.findByIdAndInsuredIdIn(anyLong(), any())).thenReturn(Optional.empty());
        assertThrows(ContractNotFoundException.class, () -> service.demander(45L, 500L, "MODIFICATION", null));
    }

    @Test
    void detail_contratD_unTiersOuInconnu_estUn404() {
        when(contrats.findByIdAndInsuredIdIn(anyLong(), any())).thenReturn(Optional.empty());
        assertThrows(ContractNotFoundException.class, () -> service.detail(45L, 500L));
    }
}
