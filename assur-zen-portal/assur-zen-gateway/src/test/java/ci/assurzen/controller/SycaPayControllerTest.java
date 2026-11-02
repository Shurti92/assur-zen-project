package ci.assurzen.controller;

import ci.assurzen.dto.PageResponse;
import ci.assurzen.sycapay.dto.SycaPayBalanceEntry;
import ci.assurzen.sycapay.dto.SycaPayBalanceResponse;
import ci.assurzen.sycapay.dto.SycaPayDashboardResponse;
import ci.assurzen.sycapay.dto.SycaPayStatusCountsResponse;
import ci.assurzen.sycapay.dto.SycaPayTransactionResponse;
import ci.assurzen.sycapay.entity.SycaPayBeneficiaryType;
import ci.assurzen.sycapay.entity.SycaPayTransaction;
import ci.assurzen.sycapay.entity.SycaPayTransactionStatus;
import ci.assurzen.sycapay.exception.SycaPayAuthenticationException;
import ci.assurzen.sycapay.exception.SycaPayBalanceException;
import ci.assurzen.sycapay.exception.SycaPayDuplicatePaymentException;
import ci.assurzen.sycapay.service.SycaPayBalanceService;
import ci.assurzen.sycapay.service.SycaPayMonitoringService;
import ci.assurzen.sycapay.service.SycaPayStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de SycaPayController tel que le consomme le frontend (routes, parametres, format JSON,
 * codes d'erreur via GlobalExceptionHandler). La securite (hasRole ADMIN) est couverte separement par
 * SycaPaySecurityConfigTest ; ici les services sont mockes, requireAdmin() n'est donc pas en jeu.
 */
class SycaPayControllerTest {

    private static final String BASE = "/api-assur-zen/sycapay";

    private SycaPayBalanceService balanceService;
    private SycaPayMonitoringService monitoringService;
    private SycaPayStatusService statusService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        balanceService = mock(SycaPayBalanceService.class);
        monitoringService = mock(SycaPayMonitoringService.class);
        statusService = mock(SycaPayStatusService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SycaPayController(balanceService, monitoringService, statusService))
                .addPlaceholderValue("env.ws", "/api-assur-zen/")
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void balance_sansParametre_utiliseLeCache_etExposeLeFormatSycaPay() throws Exception {
        when(balanceService.checkBalance(false)).thenReturn(new SycaPayBalanceResponse("checkcashoutsolde", "OK",
                List.of(new SycaPayBalanceEntry("W1", "150000.00", "Wave", "CI", "M123"))));

        mockMvc.perform(get(BASE + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Apiname").value("checkcashoutsolde"))
                .andExpect(jsonPath("$.Resultats[0].operateur").value("Wave"))
                .andExpect(jsonPath("$.Resultats[0].solde").value("150000.00"));

        verify(balanceService).checkBalance(false);
    }

    @Test
    void balance_refreshTrue_forceUnAppelDirect() throws Exception {
        when(balanceService.checkBalance(true)).thenReturn(new SycaPayBalanceResponse("checkcashoutsolde", "OK", List.of()));

        mockMvc.perform(get(BASE + "/balance").param("refresh", "true"))
                .andExpect(status().isOk());

        verify(balanceService).checkBalance(true);
        verify(balanceService, never()).checkBalance(false);
    }

    @Test
    void dashboard_renvoieLesCompteursDuJour() throws Exception {
        when(monitoringService.dashboard()).thenReturn(new SycaPayDashboardResponse(5, 250000, 3, 1, 0, 1, true, "0700000099", 100L));

        mockMvc.perform(get(BASE + "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paiementsDuJour").value(5))
                .andExpect(jsonPath("$.montantPayeAujourdhui").value(250000))
                .andExpect(jsonPath("$.paiementsSuccess").value(3))
                .andExpect(jsonPath("$.paiementsFailed").value(1))
                .andExpect(jsonPath("$.paiementsPending").value(0))
                .andExpect(jsonPath("$.paiementsUnknown").value(1))
                .andExpect(jsonPath("$.testMode").value(true))
                .andExpect(jsonPath("$.testPhone").value("0700000099"))
                .andExpect(jsonPath("$.testAmount").value(100));
    }

    @Test
    void transactions_sansFiltre_appliqueLesValeursParDefautDePagination() throws Exception {
        when(monitoringService.search(null, null, null, null, null, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(transactionResponse(1L, SycaPayTransactionStatus.SUCCESS)), 0, 20, 1, 1));

        mockMvc.perform(get(BASE + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionReference").value("SYC2026000001"))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void transactions_transmetTousLesFiltresAuService() throws Exception {
        Instant debut = Instant.parse("2026-09-01T00:00:00Z");
        Instant fin = Instant.parse("2026-09-30T23:59:59Z");
        when(monitoringService.search("SYC2026", 42L, null, null, null, SycaPayTransactionStatus.UNKNOWN, debut, fin, 2, 50))
                .thenReturn(new PageResponse<>(List.of(), 2, 50, 0, 0));

        mockMvc.perform(get(BASE + "/transactions")
                        .param("reference", "SYC2026")
                        .param("commercialId", "42")
                        .param("status", "UNKNOWN")
                        .param("dateStart", "2026-09-01T00:00:00Z")
                        .param("dateEnd", "2026-09-30T23:59:59Z")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));

        verify(monitoringService).search("SYC2026", 42L, null, null, null, SycaPayTransactionStatus.UNKNOWN, debut, fin, 2, 50);
    }

    /** Paiement des commissions partenaire (2026-09-23) : filtre par type de beneficiaire et partenaire,
     * sur la liste comme sur les compteurs par statut. */
    @Test
    void transactionsEtCompteurs_filtrentParBeneficiairePartenaire() throws Exception {
        when(monitoringService.search(null, null, 7L, null, SycaPayBeneficiaryType.PARTENAIRE, null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(new SycaPayTransactionResponse(9L, "SYC2026000009", 30L,
                        SycaPayBeneficiaryType.PARTENAIRE, null, 7L, 12000L, "XOF", "Koffi Partenaire", "0700000007",
                        SycaPayTransactionStatus.SUCCESS, "REF", "TX", 0, "OK", null, null, null, 1, null, null, true)), 0, 20, 1, 1));
        when(monitoringService.statusCounts(null, null, 7L, null, SycaPayBeneficiaryType.PARTENAIRE, null, null))
                .thenReturn(new SycaPayStatusCountsResponse(1, 0, 0, 1, 0, 0, 0));

        mockMvc.perform(get(BASE + "/transactions").param("partenaireId", "7").param("beneficiaryType", "PARTENAIRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].beneficiaryType").value("PARTENAIRE"))
                .andExpect(jsonPath("$.content[0].partenaireId").value(7))
                .andExpect(jsonPath("$.content[0].commercialId").doesNotExist())
                .andExpect(jsonPath("$.content[0].testMode").value(true));
        mockMvc.perform(get(BASE + "/transactions/status-counts").param("partenaireId", "7").param("beneficiaryType", "PARTENAIRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.SUCCESS").value(1));
        mockMvc.perform(get(BASE + "/transactions").param("beneficiaryType", "FOURNISSEUR"))
                .andExpect(status().isBadRequest());
    }

    /** Filtre contractId (2026-09-25) : apres un 409 SYCAPAY_DUPLICATE_PAYMENT, le frontend retrouve la
     * transaction du contrat pour proposer la reconciliation. Aucun resultat = page vide, pas de 404. */
    @Test
    void transactionsEtCompteurs_filtrentParContrat() throws Exception {
        when(monitoringService.search(null, null, null, 12L, null, null, null, null, 0, 1))
                .thenReturn(new PageResponse<>(List.of(), 0, 1, 0, 0));
        when(monitoringService.statusCounts(null, null, null, 12L, null, null, null))
                .thenReturn(new SycaPayStatusCountsResponse(0, 0, 0, 0, 0, 0, 0));

        mockMvc.perform(get(BASE + "/transactions").param("contractId", "12").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(BASE + "/transactions/status-counts").param("contractId", "12"))
                .andExpect(status().isOk());

        verify(monitoringService).search(null, null, null, 12L, null, null, null, null, 0, 1);
        verify(monitoringService).statusCounts(null, null, null, 12L, null, null, null);
    }

    @Test
    void transactions_statutInconnu_renvoie400SansAppelerLeService() throws Exception {
        mockMvc.perform(get(BASE + "/transactions").param("status", "PAYE"))
                .andExpect(status().isBadRequest());

        verify(monitoringService, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void transactions_dateMalFormee_renvoie400() throws Exception {
        mockMvc.perform(get(BASE + "/transactions").param("dateStart", "01/09/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusCounts_renvoieLesSixStatutsEtLeTotal_avecLesMemesFiltresQueLaRecherche() throws Exception {
        Instant debut = Instant.parse("2026-09-01T00:00:00Z");
        when(monitoringService.statusCounts("SYC2026", 42L, null, null, null, debut, null))
                .thenReturn(new SycaPayStatusCountsResponse(3, 0, 0, 2, 1, 0, 0));

        MvcResult result = mockMvc.perform(get(BASE + "/transactions/status-counts")
                        .param("reference", "SYC2026")
                        .param("commercialId", "42")
                        .param("dateStart", "2026-09-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.PENDING").value(0))
                .andExpect(jsonPath("$.PROCESSING").value(0))
                .andExpect(jsonPath("$.SUCCESS").value(2))
                .andExpect(jsonPath("$.FAILED").value(1))
                .andExpect(jsonPath("$.CANCELLED").value(0))
                .andExpect(jsonPath("$.UNKNOWN").value(0))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("\"pending\"") || body.contains("\"success\""),
                "Pas de doublon en minuscules dans le JSON : " + body);
        verify(monitoringService, never()).getById(anyLong());
    }

    @Test
    void statusCounts_dateInvalide_renvoie400() throws Exception {
        mockMvc.perform(get(BASE + "/transactions/status-counts").param("dateEnd", "hier"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transactionParId_renvoieLeDetail() throws Exception {
        when(monitoringService.getById(7L)).thenReturn(transactionResponse(7L, SycaPayTransactionStatus.FAILED));

        mockMvc.perform(get(BASE + "/transactions/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.beneficiaryPhone").value("0700000000"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void transactionParId_introuvable_renvoie404AvecLeMessageMetier() throws Exception {
        when(monitoringService.getById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction SycaPay introuvable: 99"));

        mockMvc.perform(get(BASE + "/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction SycaPay introuvable: 99"));
    }

    @Test
    void transactionParId_idNonNumerique_renvoie400() throws Exception {
        mockMvc.perform(get(BASE + "/transactions/abc"))
                .andExpect(status().isBadRequest());

        verify(monitoringService, never()).getById(anyLong());
    }

    @Test
    void reconcile_renvoieLaTransactionMiseAJour() throws Exception {
        SycaPayTransaction transaction = new SycaPayTransaction();
        transaction.setId(12L);
        transaction.setTransactionReference("SYC2026000012");
        transaction.setContractId(3L);
        transaction.setCommercialId(4L);
        transaction.setAmount(50000L);
        transaction.setCurrency("XOF");
        transaction.setStatus(SycaPayTransactionStatus.SUCCESS);
        when(statusService.reconcile(12L)).thenReturn(transaction);

        mockMvc.perform(post(BASE + "/transactions/12/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.transactionReference").value("SYC2026000012"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void reconcile_statutNonReconciliable_renvoie409() throws Exception {
        when(statusService.reconcile(eq(12L))).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Transaction SYC2026000012 en statut SUCCESS : rien a reconcilier (reserve a PROCESSING/UNKNOWN)"));

        mockMvc.perform(post(BASE + "/transactions/12/reconcile"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Transaction SYC2026000012 en statut SUCCESS : rien a reconcilier (reserve a PROCESSING/UNKNOWN)"));
    }

    @Test
    void balance_sycaPayInjoignable_renvoie502AvecMessageGenerique() throws Exception {
        when(balanceService.checkBalance(false))
                .thenThrow(new SycaPayBalanceException("Echec technique de l'appel de consultation du solde SycaPay"));

        MvcResult result = mockMvc.perform(get(BASE + "/balance"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SYCAPAY_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Service de paiement SycaPay indisponible, veuillez reessayer plus tard"))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("Echec technique"),
                "Le message brut de l'exception ne doit jamais etre expose");
    }

    @Test
    void reconcile_authentificationSycaPayEchouee_renvoie502() throws Exception {
        when(statusService.reconcile(12L)).thenThrow(new SycaPayAuthenticationException("Reponse vide de SycaPay (HTTP 500)"));

        mockMvc.perform(post(BASE + "/transactions/12/reconcile"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SYCAPAY_UNAVAILABLE"));
    }

    @Test
    void paiementEnDouble_renvoie409EtNon502() throws Exception {
        when(statusService.reconcile(12L)).thenThrow(new SycaPayDuplicatePaymentException(
                "Un paiement SycaPay est deja en cours ou effectue pour le contrat POL-123."));

        mockMvc.perform(post(BASE + "/transactions/12/reconcile"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SYCAPAY_DUPLICATE_PAYMENT"))
                .andExpect(jsonPath("$.message").value("Un paiement SycaPay est deja en cours ou effectue pour le contrat POL-123."));
    }

    @Test
    void reconcile_enGet_renvoie405() throws Exception {
        mockMvc.perform(get(BASE + "/transactions/12/reconcile"))
                .andExpect(status().isMethodNotAllowed());
    }

    /** Garde-fou : aucune reponse de ce controller ne doit jamais contenir de secret SycaPay. */
    @Test
    void aucuneReponseNExposeDeSecret() throws Exception {
        when(monitoringService.getById(7L)).thenReturn(transactionResponse(7L, SycaPayTransactionStatus.SUCCESS));
        when(balanceService.checkBalance(false)).thenReturn(new SycaPayBalanceResponse("checkcashoutsolde", "OK",
                List.of(new SycaPayBalanceEntry("W1", "0.00", "Wave", "CI", "M123"))));

        for (String url : List.of(BASE + "/transactions/7", BASE + "/balance")) {
            MvcResult result = mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            String body = result.getResponse().getContentAsString().toLowerCase();
            assertFalse(body.contains("apikey") || body.contains("api_key") || body.contains("secret")
                    || body.contains("token"), "Secret SycaPay expose sur " + url + " : " + body);
        }
    }

    private static SycaPayTransactionResponse transactionResponse(Long id, SycaPayTransactionStatus status) {
        return new SycaPayTransactionResponse(id, "SYC2026000001", 3L, SycaPayBeneficiaryType.COMMERCIAL, 4L, null, 50000L, "XOF", "Kouassi Jean", "0700000000",
                status, "REF-SYCA", "TX-1", 0, "Operation reussie",
                Instant.parse("2026-09-22T10:00:00Z"), Instant.parse("2026-09-22T10:00:01Z"),
                Instant.parse("2026-09-22T10:00:05Z"), 1, null, null, false);
    }
}
