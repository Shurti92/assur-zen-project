package ci.assurzen.service;

import ci.assurzen.dto.MobileClaimDtos;
import ci.assurzen.entities.ClientAccountInsured;
import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Insured;
import ci.assurzen.entities.Produit;
import ci.assurzen.entities.Sinistre;
import ci.assurzen.entities.SinistreStatus;
import ci.assurzen.entities.User;
import ci.assurzen.exception.ContractNotFoundException;
import ci.assurzen.exception.MobileApiException;
import ci.assurzen.metrics.BusinessMetrics;
import ci.assurzen.repositories.ClientAccountInsuredRepository;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.SinistrePhotoRepository;
import ci.assurzen.repositories.SinistreRepository;
import ci.assurzen.repositories.SinistreSuiviRepository;
import ci.assurzen.repositories.UserRepository;
import ci.assurzen.services.CodeGeneratorService;
import ci.assurzen.services.DynamicConfigurationService;
import ci.assurzen.services.SinistrePhotoStorageService;
import ci.assurzen.services.SmsNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sinistres mobiles (demande n°6, 2026-11-02). */
class MobileClaimServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now(ZoneId.of("Africa/Abidjan"));
    private ClientAccountInsuredRepository liens;
    private ContractRepository contrats;
    private SinistreRepository sinistres;
    private SinistrePhotoRepository photos;
    private SmsNotificationService sms;
    private MobileClaimService service;
    private Contract actif;
    private Contract attente;

    @BeforeEach
    void setUp() {
        liens = mock(ClientAccountInsuredRepository.class);
        contrats = mock(ContractRepository.class);
        sinistres = mock(SinistreRepository.class);
        photos = mock(SinistrePhotoRepository.class);
        sms = mock(SmsNotificationService.class);
        UserRepository users = mock(UserRepository.class);
        CodeGeneratorService codes = mock(CodeGeneratorService.class);
        DynamicConfigurationService config = mock(DynamicConfigurationService.class);
        when(users.findById(45L)).thenReturn(Optional.of(new User()));
        when(codes.generate(any())).thenReturn("SIN2026000009");
        when(config.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(liens.findByUserId(45L)).thenReturn(List.of(new ClientAccountInsured(45L, 627L)));
        when(sinistres.save(any(Sinistre.class))).thenAnswer(inv -> {
            Sinistre s = inv.getArgument(0);
            s.setId(12L);
            return s;
        });
        service = new MobileClaimService(liens, contrats, sinistres, mock(SinistreSuiviRepository.class), photos,
                mock(SinistrePhotoStorageService.class), users, codes, sms, config, mock(BusinessMetrics.class));

        Insured assure = new Insured();
        assure.setId(627L);
        assure.setClientPhone("0500000001");
        actif = contrat(655L, ContractStatus.signed, "Assurance Moto", assure);
        attente = contrat(654L, ContractStatus.pending, "Assurance Sante", assure);
        when(contrats.findByIdAndInsuredIdIn(655L, List.of(627L))).thenReturn(Optional.of(actif));
        when(contrats.findByIdAndInsuredIdIn(654L, List.of(627L))).thenReturn(Optional.of(attente));
        when(contrats.findByIdAndInsuredIdIn(1L, List.of(627L))).thenReturn(Optional.empty());
    }

    private Contract contrat(long id, ContractStatus statut, String produit, Insured assure) {
        Contract c = new Contract();
        c.setId(id);
        c.setPolicyNumber("P-" + id);
        c.setStatus(statut);
        c.setDateEffet(AUJOURDHUI.minusMonths(6));
        c.setDateEcheance(AUJOURDHUI.plusMonths(6));
        Produit p = new Produit();
        p.setNomProduit(produit);
        c.setProduct(p);
        c.setInsured(assure);
        return c;
    }

    private MobileClaimDtos.CreateRequest requete(String contrat, String type, LocalDate date) {
        return new MobileClaimDtos.CreateRequest(contrat, type, date, "Cocody", true,
                new MobileClaimDtos.ThirdParty("Kone", "1234AB01", "Allianz"), "Choc a l'arriere");
    }

    @Test
    void declaration_surContratActif_creeLeSinistreEtEnvoieLeSms() {
        MobileClaimDtos.Created cree = service.declarer(45L, requete("655", "COLLISION", AUJOURDHUI.minusDays(3)));
        assertEquals("SIN2026000009", cree.number());
        assertEquals("DECLARED", cree.status());
        verify(sms).sendClaimDeclaredSms("0500000001", "SIN2026000009");
    }

    @Test
    void declaration_surContratEnAttente_donneClaimNotAllowed() {
        MobileApiException e = assertThrows(MobileApiException.class, () -> service.declarer(45L, requete("654", "CARE", AUJOURDHUI)));
        assertEquals("CLAIM_NOT_ALLOWED", e.getCode());
        assertEquals(409, e.getHttpStatus());
        verify(sms, never()).sendClaimDeclaredSms(any(), any());
    }

    @Test
    void declaration_contratD_unTiers_donne404() {
        assertThrows(ContractNotFoundException.class, () -> service.declarer(45L, requete("1", "COLLISION", AUJOURDHUI)));
        assertThrows(ContractNotFoundException.class, () -> service.declarer(45L, requete("abc", "COLLISION", AUJOURDHUI)));
    }

    @Test
    void declaration_dateFutureOuHorsContrat_estRefusee() {
        assertEquals("OCCURRED_AT_OUT_OF_RANGE", assertThrows(MobileApiException.class,
                () -> service.declarer(45L, requete("655", "COLLISION", AUJOURDHUI.plusDays(1)))).getCode());
        assertEquals("OCCURRED_AT_OUT_OF_RANGE", assertThrows(MobileApiException.class,
                () -> service.declarer(45L, requete("655", "COLLISION", AUJOURDHUI.minusYears(2)))).getCode());
    }

    @Test
    void declaration_typeInconnuPourLeProduit_estRefuse() {
        MobileApiException e = assertThrows(MobileApiException.class, () -> service.declarer(45L, requete("655", "HOSPITALIZATION", AUJOURDHUI)));
        assertEquals("typeId", e.getField());
    }

    @Test
    void declaration_limiteDeSinistresOuverts() {
        when(sinistres.countByContractIdAndStatusNotIn(anyLong(), any())).thenReturn(5L);
        assertEquals("CLAIM_LIMIT_REACHED", assertThrows(MobileApiException.class,
                () -> service.declarer(45L, requete("655", "COLLISION", AUJOURDHUI))).getCode());
    }

    @Test
    void types_selonLeProduit() {
        assertEquals("COLLISION", service.types("moto").items().get(0).id());
        assertEquals("HOSPITALIZATION", service.types("sante").items().get(0).id());
        assertEquals("OTHER", service.types("inconnu").items().get(0).id());
    }

    @Test
    void etapes_correspondentAuxStatutsReels() {
        assertEquals(0, MobileClaimService.etapeCourante(SinistreStatus.declare));
        assertEquals(1, MobileClaimService.etapeCourante(SinistreStatus.pieces_complementaires_requises));
        assertEquals(2, MobileClaimService.etapeCourante(SinistreStatus.expertise_en_cours));
        assertEquals(3, MobileClaimService.etapeCourante(SinistreStatus.rejete));
        assertEquals(4, MobileClaimService.etapeCourante(SinistreStatus.cloture));
    }

    @Test
    void typeDeFichier_estDetecteParLesPremiersOctets() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] pdf = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        assertEquals("JPEG", MobileClaimService.detecterType(new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg)));
        assertEquals("PNG", MobileClaimService.detecterType(new MockMultipartFile("file", "a.png", "image/png", png)));
        assertEquals("PDF", MobileClaimService.detecterType(new MockMultipartFile("file", "a.pdf", "application/pdf", pdf)));
        // un executable renomme en .jpg n'est pas accepte
        assertEquals("INCONNU", MobileClaimService.detecterType(new MockMultipartFile("file", "a.jpg", "image/jpeg", "MZ-exe".getBytes())));
        assertTrue(true);
    }
}
