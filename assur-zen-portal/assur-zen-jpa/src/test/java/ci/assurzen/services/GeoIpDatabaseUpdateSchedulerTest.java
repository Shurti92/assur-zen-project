package ci.assurzen.services;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Le job mensuel ne doit jamais tenter de telecharger quoi que ce soit (ni recharger
 * GeoIpLookupService) tant que le compte MaxMind (account-id/license-key) n'est pas configure -
 * c'est le seul comportement testable sans appel reseau reel vers download.maxmind.com.
 */
class GeoIpDatabaseUpdateSchedulerTest {

    @Test
    void updateDatabases_sansCompteMaxmindConfigure_neFaitRienEtNeLeveJamais() {
        GeoIpLookupService geoIpLookupService = mock(GeoIpLookupService.class);
        GeoIpDatabaseUpdateScheduler scheduler = new GeoIpDatabaseUpdateScheduler(geoIpLookupService);
        ReflectionTestUtils.setField(scheduler, "accountId", "");
        ReflectionTestUtils.setField(scheduler, "licenseKey", "");

        scheduler.updateDatabases();

        verifyNoInteractions(geoIpLookupService);
    }

    @Test
    void updateDatabases_seulAccountIdConfigure_neFaitRienEtNeLeveJamais() {
        GeoIpLookupService geoIpLookupService = mock(GeoIpLookupService.class);
        GeoIpDatabaseUpdateScheduler scheduler = new GeoIpDatabaseUpdateScheduler(geoIpLookupService);
        ReflectionTestUtils.setField(scheduler, "accountId", "1402228");
        ReflectionTestUtils.setField(scheduler, "licenseKey", "");

        scheduler.updateDatabases();

        verify(geoIpLookupService, never()).reload();
    }
}
