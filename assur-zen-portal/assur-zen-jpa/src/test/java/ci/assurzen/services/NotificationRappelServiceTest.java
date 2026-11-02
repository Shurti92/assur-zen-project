package ci.assurzen.services;

import ci.assurzen.entities.Contract;
import ci.assurzen.entities.ContractStatus;
import ci.assurzen.entities.Partenaire;
import ci.assurzen.entities.PartenaireDocument;
import ci.assurzen.entities.PartenaireNotificationType;
import ci.assurzen.entities.StatutReversementPrime;
import ci.assurzen.events.NotificationUtilisateurEvent;
import ci.assurzen.repositories.ContractRepository;
import ci.assurzen.repositories.PartenaireDocumentRepository;
import ci.assurzen.repositories.PartenaireNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Rappels automatiques de l'Espace Partenaire (2026-10-04). */
class NotificationRappelServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 10, 5);

    private final ContractRepository contractRepository = mock(ContractRepository.class);
    private final PartenaireDocumentRepository documentRepository = mock(PartenaireDocumentRepository.class);
    private final PartenaireNotificationRepository notificationRepository = mock(PartenaireNotificationRepository.class);
    private final DynamicConfigurationService configuration = mock(DynamicConfigurationService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationRappelService service = new NotificationRappelService(contractRepository, documentRepository,
            notificationRepository, configuration, eventPublisher);

    private final Partenaire partenaire = Partenaire.builder().id(13L).build();

    @BeforeEach
    void setUp() {
        when(configuration.getBoolean(NotificationRappelService.CLE_ACTIVATION, false)).thenReturn(true);
        when(configuration.getInteger(NotificationRappelService.CLE_DELAI_PRIME, 7)).thenReturn(7);
        when(contractRepository.findByStatusAndPartenaireIsNotNullAndDateEcheanceIn(any(), any())).thenReturn(List.of());
        when(contractRepository.findByStatusAndPrimeReversementStatusAndPartenaireIsNotNull(any(), any())).thenReturn(List.of());
        when(documentRepository.findByDateExpirationIn(any())).thenReturn(List.of());
    }

    @Test
    void desactive_aucunRappel() {
        when(configuration.getBoolean(NotificationRappelService.CLE_ACTIVATION, false)).thenReturn(false);

        assertEquals(0, service.executer(AUJOURDHUI));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void echeanceAJ7_rappelAuPartenaire_saufSiDejaEmisAujourdhui() {
        Contract contrat = Contract.builder().id(452L).policyNumber("TEST-1").partenaire(partenaire).premium(150000L)
                .status(ContractStatus.signed).dateEcheance(AUJOURDHUI.plusDays(7)).build();
        when(contractRepository.findByStatusAndPartenaireIsNotNullAndDateEcheanceIn(eq(ContractStatus.signed),
                eq(List.of(AUJOURDHUI.plusDays(30), AUJOURDHUI.plusDays(7))))).thenReturn(List.of(contrat));

        assertEquals(1, service.executer(AUJOURDHUI));
        ArgumentCaptor<NotificationUtilisateurEvent> event = ArgumentCaptor.forClass(NotificationUtilisateurEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(13L, event.getValue().partenaireId());
        assertEquals(PartenaireNotificationType.ECHEANCE_PROCHE, event.getValue().type());
        assertEquals("CONTRAT", event.getValue().objetType());
        assertEquals(452L, event.getValue().objetId());
        assertEquals("Échéance dans 7 jours", event.getValue().titre());

        when(notificationRepository.existsByTypeAndObjetTypeAndObjetIdAndCreatedAtGreaterThanEqual(
                eq(PartenaireNotificationType.ECHEANCE_PROCHE), eq("CONTRAT"), eq(452L), any())).thenReturn(true);
        assertEquals(0, service.executer(AUJOURDHUI));
    }

    @Test
    void primeNonReversee_uneSeuleFois_auDelaiConfigure() {
        Contract signeIlYA7Jours = Contract.builder().id(1L).policyNumber("P1").partenaire(partenaire).premium(80000L)
                .status(ContractStatus.signed).primeReversementStatus(StatutReversementPrime.non_reverse)
                .signedAt(AUJOURDHUI.minusDays(7)).build();
        Contract signeHier = Contract.builder().id(2L).policyNumber("P2").partenaire(partenaire).premium(50000L)
                .status(ContractStatus.signed).primeReversementStatus(StatutReversementPrime.non_reverse)
                .signedAt(AUJOURDHUI.minusDays(1)).build();
        when(contractRepository.findByStatusAndPrimeReversementStatusAndPartenaireIsNotNull(ContractStatus.signed,
                StatutReversementPrime.non_reverse)).thenReturn(List.of(signeIlYA7Jours, signeHier));

        assertEquals(1, service.executer(AUJOURDHUI));

        when(notificationRepository.existsByTypeAndObjetTypeAndObjetId(PartenaireNotificationType.PRIME_NON_REVERSEE, "CONTRAT", 1L))
                .thenReturn(true);
        assertEquals(0, service.executer(AUJOURDHUI));
    }

    @Test
    void documents_expirantEtExpire() {
        PartenaireDocument cni = PartenaireDocument.builder().id(5L).type("CNI").partenaire(partenaire)
                .dateExpiration(AUJOURDHUI).build();
        PartenaireDocument permis = PartenaireDocument.builder().id(6L).type("PERMIS").partenaire(partenaire)
                .dateExpiration(AUJOURDHUI.plusDays(30)).build();
        when(documentRepository.findByDateExpirationIn(any())).thenReturn(List.of(cni, permis));

        assertEquals(2, service.executer(AUJOURDHUI));
        ArgumentCaptor<NotificationUtilisateurEvent> events = ArgumentCaptor.forClass(NotificationUtilisateurEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertEquals(PartenaireNotificationType.DOCUMENT_EXPIRE, events.getAllValues().get(0).type());
        assertEquals(PartenaireNotificationType.DOCUMENT_EXPIRE_BIENTOT, events.getAllValues().get(1).type());
        assertEquals("DOCUMENT", events.getAllValues().get(1).objetType());
    }
}
