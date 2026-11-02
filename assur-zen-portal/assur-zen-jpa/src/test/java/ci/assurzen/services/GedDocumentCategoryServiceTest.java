package ci.assurzen.services;

import ci.assurzen.audit.FunctionalAuditRecorder;
import ci.assurzen.dto.GedDocumentCategoryRequest;
import ci.assurzen.entities.GedDocumentCategory;
import ci.assurzen.repositories.GedDocumentCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GedDocumentCategoryServiceTest {

    private record Fixture(GedDocumentCategoryService service, GedDocumentCategoryRepository repository) {
    }

    private Fixture newFixture() {
        GedDocumentCategoryRepository repository = mock(GedDocumentCategoryRepository.class);
        FunctionalAuditRecorder functionalAuditRecorder = mock(FunctionalAuditRecorder.class);
        when(repository.save(any())).thenAnswer(inv -> {
            GedDocumentCategory c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });
        return new Fixture(new GedDocumentCategoryService(repository, functionalAuditRecorder), repository);
    }

    @Test
    void create_codeDejaExistant_estRejeteAvec409() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeIgnoreCase("CLAIM"))
                .thenReturn(Optional.of(GedDocumentCategory.builder().id(1L).code("CLAIM").nom("Sinistre").build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> fx.service().create(new GedDocumentCategoryRequest("CLAIM", "Sinistre bis", null, null, null)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void delete_categorieSysteme_estRejeteAvec409() {
        Fixture fx = newFixture();
        when(fx.repository().findById(1L))
                .thenReturn(Optional.of(GedDocumentCategory.builder().id(1L).code("OTHER").nom("Autre").systeme(true).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> fx.service().delete(1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void create_categorieValide_estCreee() {
        Fixture fx = newFixture();
        when(fx.repository().findByCodeIgnoreCase("LEGAL")).thenReturn(Optional.empty());

        var response = fx.service().create(new GedDocumentCategoryRequest("LEGAL", "Juridique", null, null, null));

        assertEquals("LEGAL", response.code());
        assertEquals(true, response.actif());
    }
}
