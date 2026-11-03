package ci.assurzen.repositories;

import ci.assurzen.entities.QuoteTariff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteTariffRepository extends JpaRepository<QuoteTariff, Long> {

    Optional<QuoteTariff> findByProductKeyAndFormulaIdAndVehicleCategoryAndDurationMonths(
            String productKey, String formulaId, String vehicleCategory, Integer durationMonths);

    List<QuoteTariff> findByProductKeyAndActiveTrue(String productKey);

    List<QuoteTariff> findAllByOrderByProductKeyAscFormulaIdAscVehicleCategoryAscDurationMonthsAsc();
}
