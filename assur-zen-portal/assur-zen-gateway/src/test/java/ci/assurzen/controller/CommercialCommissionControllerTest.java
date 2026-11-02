package ci.assurzen.controller;

import ci.assurzen.dto.CommercialCommissionPaymentRequest;
import ci.assurzen.dto.CommercialCommissionPaymentResponse;
import ci.assurzen.dto.PageResponse;
import ci.assurzen.services.CommercialCommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat HTTP de POST /commercial-commissions/pay depuis l'integration SycaPay (2026-09-22) : chaque
 * contrat de la requete revient avec son propre sycapayStatus, que le frontend DOIT lire (un 201 global
 * ne signifie pas que tous les virements Wave ont reussi).
 */
class CommercialCommissionControllerTest {

    private static final String BASE = "/api-assur-zen/commercial-commissions";

    private CommercialCommissionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(CommercialCommissionService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommercialCommissionController(service))
                .addPlaceholderValue("env.ws", "/api-assur-zen/")
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void pay_renvoie201_avecLeStatutSycaPayDeChaqueContrat() throws Exception {
        when(service.pay(new CommercialCommissionPaymentRequest(List.of(10L, 11L, 12L), "LOT-SEPT")))
                .thenReturn(List.of(
                        new CommercialCommissionPaymentResponse(1L, 10L, "POL-10", 4L, "Kouassi Jean", 50000L, 1L, "admin",
                                "LOT-SEPT", Instant.parse("2026-09-22T10:00:05Z"), true,
                                "SUCCESS", "SYC2026000001", "Operation reussie", 101L, "VIREMENT_SYCAPAY"),
                        new CommercialCommissionPaymentResponse(null, 11L, "POL-11", 4L, "Kouassi Jean", 30000L, null, null,
                                "LOT-SEPT", null, false,
                                "UNKNOWN", "SYC2026000002", "Statut incertain - reconciliation requise", 102L, "VIREMENT_SYCAPAY"),
                        new CommercialCommissionPaymentResponse(null, 12L, "POL-12", 4L, "Kouassi Jean", 20000L, null, null,
                                "LOT-SEPT", null, false,
                                "REJECTED", null, "Paiement deja en cours pour ce contrat", null, "VIREMENT_SYCAPAY")));

        mockMvc.perform(post(BASE + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractIds\":[10,11,12],\"reference\":\"LOT-SEPT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].contractId").value(10))
                .andExpect(jsonPath("$[0].sycapayStatus").value("SUCCESS"))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].sycapayTransactionReference").value("SYC2026000001"))
                .andExpect(jsonPath("$[1].contractId").value(11))
                .andExpect(jsonPath("$[1].sycapayStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$[1].id").doesNotExist())
                .andExpect(jsonPath("$[1].paidAt").doesNotExist())
                .andExpect(jsonPath("$[0].sycapayTransactionPk").value(101))
                .andExpect(jsonPath("$[1].sycapayTransactionPk").value(102))
                .andExpect(jsonPath("$[2].sycapayStatus").value("REJECTED"))
                .andExpect(jsonPath("$[2].sycapayTransactionReference").doesNotExist())
                .andExpect(jsonPath("$[2].sycapayTransactionPk").doesNotExist());
    }

    @Test
    void pay_listeDeContratsVide_renvoie400SansDeclencherDePaiement() throws Exception {
        mockMvc.perform(post(BASE + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractIds\":[]}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).pay(any());
    }

    @Test
    void pay_sansContractIds_renvoie400SansDeclencherDePaiement() throws Exception {
        mockMvc.perform(post(BASE + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"LOT\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).pay(any());
    }

    @Test
    void pay_referenceTropLongue_renvoie400() throws Exception {
        mockMvc.perform(post(BASE + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractIds\":[10],\"reference\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).pay(any());
    }

    @Test
    void pay_corpsIllisible_renvoie400() throws Exception {
        mockMvc.perform(post(BASE + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{pas du json"))
                .andExpect(status().isBadRequest());

        verify(service, never()).pay(any());
    }

    @Test
    void pay_conflitMetier_renvoie409AvecLeMessage() throws Exception {
        when(service.pay(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Commission deja payee pour le contrat POL-10"));

        mockMvc.perform(post(BASE + "/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractIds\":[10]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Commission deja payee pour le contrat POL-10"));
    }

    @Test
    void historiquePaiements_valeursParDefaut_etFiltreCommercial() throws Exception {
        when(service.getPaymentHistory(null, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        when(service.getPaymentHistory(4L, 1, 10)).thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0));

        mockMvc.perform(get(BASE + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));
        mockMvc.perform(get(BASE + "/payments").param("commercialId", "4").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));

        verify(service).getPaymentHistory(null, 0, 20);
        verify(service).getPaymentHistory(4L, 1, 10);
    }
}
