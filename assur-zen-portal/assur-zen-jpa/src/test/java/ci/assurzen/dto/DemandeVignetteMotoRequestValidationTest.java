package ci.assurzen.dto;

import ci.assurzen.entities.TypeDemandeVignette;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifie directement (Jakarta Validation, sans contexte Spring) les contraintes §13 les plus
 * sensibles : date de mise en circulation dans le futur (aucun test de service ne peut couvrir @Valid,
 * qui n'intervient qu'au niveau du controller) et cylindree invalide.
 */
class DemandeVignetteMotoRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private DemandeVignetteMotoRequest request(LocalDate datePremiereMiseCirculation, Integer cylindree) {
        return new DemandeVignetteMotoRequest(TypeDemandeVignette.NOUVELLE_DEMANDE,
                "KOUASSI", "Jean", "0700000000", null, "Cocody", "Abidjan", "ABIDJAN",
                "Yamaha", "Crypton", "XXXXXXXXXXXX", cylindree, datePremiereMiseCirculation, null, null);
    }

    @Test
    void dateMiseEnCirculationDansLeFutur_estRejetee() {
        Set<ConstraintViolation<DemandeVignetteMotoRequest>> violations =
                validator.validate(request(LocalDate.now().plusDays(1), 110));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("datePremiereMiseCirculation")));
    }

    @Test
    void dateMiseEnCirculationAujourdhui_estAcceptee() {
        Set<ConstraintViolation<DemandeVignetteMotoRequest>> violations = validator.validate(request(LocalDate.now(), 110));
        assertFalse(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("datePremiereMiseCirculation")));
    }

    @Test
    void cylindreeZero_estRejetee() {
        Set<ConstraintViolation<DemandeVignetteMotoRequest>> violations =
                validator.validate(request(LocalDate.of(2020, 1, 1), 0));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("cylindreeMoto")));
    }

    @Test
    void cylindreeNulle_estRejetee_obligatoire() {
        Set<ConstraintViolation<DemandeVignetteMotoRequest>> violations =
                validator.validate(request(LocalDate.of(2020, 1, 1), null));

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("cylindreeMoto")));
    }

    @Test
    void requeteValide_neLeveAucuneViolation() {
        Set<ConstraintViolation<DemandeVignetteMotoRequest>> violations = validator.validate(request(LocalDate.of(2020, 5, 15), 110));
        assertEquals(0, violations.size());
    }
}
