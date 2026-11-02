package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.GedDocumentTypeRequest;
import ci.assurzen.entities.GedDocumentType;
import ci.assurzen.repositories.GedDocumentTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GedDocumentTypeServiceTest {

    private record Fixture(GedDocumentTypeService service, GedDocumentTypeRepository repository) {
    }

    private Fixture newFixture() {
        GedDocumentTypeRepository repository = mock(GedDocumentTypeRepository.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        when(repository.save(any())).thenAnswer(inv -> {
            GedDocumentType t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(1L);
            }
            return t;
        });
        return new Fixture(new GedDocumentTypeService(repository, functionalAuditRecorder), repository);
    }

    @Test
    void create_codeDejaExistant_estRejeteAvec409() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeIgnoreCase("CONTRAT"))
                .thenReturn(Optional.of(GedDocumentType.builder().id(1L).code("CONTRAT").nom("Contrat").build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(new GedDocumentTypeRequest("CONTRAT", "Contrat bis", null, null, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void delete_typeSysteme_estRejeteAvec409() {
        Fixture fx = newFixture();
        when(fx.repository().findById(1L))
                .thenReturn(Optional.of(GedDocumentType.builder().id(1L).code("AUTRE").nom("Autre").systeme(true).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void resolveOuCreerTypeParDefaut_absent_leCreeAvecCodeAutre() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeIgnoreCase("AUTRE")).thenReturn(Optional.empty());

        GedDocumentType type = fx.service().resolveOuCreerTypeParDefaut();

        assertEquals("AUTRE", type.getCode());
        assertEquals(true, type.isSysteme());
    }

    @Test
    void search_filtreParActif() {
        Fixture fx = newFixture();
        when(fx.repository().findAll()).thenReturn(List.of(
                GedDocumentType.builder().id(1L).code("A").nom("A").ordre(1).actif(true).build(),
                GedDocumentType.builder().id(2L).code("B").nom("B").ordre(2).actif(false).build()
        ));

        var page = fx.service().search(null, true, 0, 20);

        assertEquals(1, page.totalElements());
        assertEquals("A", page.content().get(0).code());
    }
}
