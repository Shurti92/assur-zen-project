package ci.assurzen.services;

import ci.assurzen.dto.SupportContactResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Contact support de l'Espace Partenaire (2026-10-04). */
class SupportContactServiceTest {

    private final DynamicConfigurationService configuration = mock(DynamicConfigurationService.class);
    private final SupportContactService service = new SupportContactService(configuration);

    @Test
    void valeursVides_null_valeursRenseignees_nettoyees() {
        when(configuration.getString(anyString(), any())).thenReturn("");
        when(configuration.getString("SUPPORT_NOM", null)).thenReturn("  Service partenaires  ");
        when(configuration.getString("SUPPORT_TELEPHONE", null)).thenReturn("2720000000");

        SupportContactResponse contact = service.contact();

        assertEquals("Service partenaires", contact.nom());
        assertEquals("2720000000", contact.telephone());
        assertNull(contact.email());
        assertNull(contact.horaires());
        assertEquals("SUPPORT", contact.source());
    }
}
